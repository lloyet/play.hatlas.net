package org.minecraft.atlas.job;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class JobRewardManager {

    public record LevelReward(List<ItemStack> items, List<PotionEffect> potions) {}

    // Job → (level → reward), sorted so subMap works correctly
    private static final Map<Job, NavigableMap<Integer, LevelReward>> rewards = new EnumMap<>(Job.class);

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    public static void load(Plugin plugin) {
        rewards.clear();
        File file = new File(plugin.getDataFolder(), "jobs.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");

        // If the data-folder file predates the rewards section, fall back to the bundled resource
        if (rewardsSection == null) {
            InputStream bundled = plugin.getResource("jobs.yml");
            if (bundled != null) {
                try (InputStreamReader reader = new InputStreamReader(bundled)) {
                    rewardsSection = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("rewards");
                } catch (Exception e) {
                    plugin.getLogger().warning("[JobRewardManager] Failed to read bundled jobs.yml: " + e.getMessage());
                }
            }
        }

        if (rewardsSection == null) {
            plugin.getLogger().warning("[JobRewardManager] No 'rewards' section found in jobs.yml");
            return;
        }

        for (Job job : Job.values()) {
            ConfigurationSection jobSection = rewardsSection.getConfigurationSection(job.name());
            if (jobSection == null) continue;

            ConfigurationSection minorSection = jobSection.getConfigurationSection("minor_rewards");
            if (minorSection == null) continue;

            NavigableMap<Integer, LevelReward> jobRewards = new TreeMap<>();

            for (String levelKey : minorSection.getKeys(false)) {
                int level;
                try { level = Integer.parseInt(levelKey); } catch (NumberFormatException e) { continue; }

                ConfigurationSection levelSection = minorSection.getConfigurationSection(levelKey);
                if (levelSection == null) continue;

                List<ItemStack> items = loadItems(levelSection, plugin);
                List<PotionEffect> potions = loadPotions(levelSection, plugin);

                if (!items.isEmpty() || !potions.isEmpty()) {
                    jobRewards.put(level, new LevelReward(items, potions));
                }
            }

            rewards.put(job, jobRewards);
    //      plugin.getLogger().info("[JobRewardManager] Loaded " + jobRewards.size() + " minor rewards for " + job.getDisplayName());
        }
    }

    // -------------------------------------------------------------------------
    // Application
    // -------------------------------------------------------------------------

    /** Applies all minor rewards for levels in (oldLevel, newLevel], in ascending order. */
    public static void applyMinorRewards(Player player, Job job, int oldLevel, int newLevel) {
        NavigableMap<Integer, LevelReward> jobRewards = rewards.get(job);
        if (jobRewards == null || jobRewards.isEmpty()) return;

        for (Map.Entry<Integer, LevelReward> entry : jobRewards.subMap(oldLevel, false, newLevel, true).entrySet()) {
            applyReward(player, job, entry.getKey(), entry.getValue());
        }
    }

    private static void applyReward(Player player, Job job, int level, LevelReward reward) {
        player.sendMessage(
                Component.text("Level " + level + " reward (", NamedTextColor.GOLD)
                        .append(Component.text(job.getDisplayName(), job.getColor()))
                        .append(Component.text("):", NamedTextColor.GOLD))
        );

        for (ItemStack item : reward.items()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            leftover.values().forEach(stack -> player.getWorld().dropItem(player.getLocation(), stack));
            String dropped = leftover.isEmpty() ? "" : " (dropped — inventory full)";
            player.sendMessage(Component.text(
                    "  + " + item.getAmount() + "x " + titleCase(item.getType().name()) + dropped,
                    NamedTextColor.YELLOW));
        }

        for (PotionEffect effect : reward.potions()) {
            player.addPotionEffect(effect);
            player.sendMessage(Component.text(
                    "  + " + titleCase(effect.getType().getKey().getKey()) + " for " + (effect.getDuration() / 20) + "s",
                    NamedTextColor.AQUA));
        }
    }

    // -------------------------------------------------------------------------
    // YAML helpers
    // -------------------------------------------------------------------------

    private static List<ItemStack> loadItems(ConfigurationSection section, Plugin plugin) {
        List<ItemStack> result = new ArrayList<>();
        List<?> raw = section.getList("items");
        if (raw == null) return result;

        for (Object obj : raw) {
            String typeName = null;
            int amount = 1;

            if (obj instanceof Map<?, ?> map) {
                Object typeVal = map.get("type");
                if (typeVal == null) continue;
                typeName = typeVal.toString();
                Object amountVal = map.get("amount");
                if (amountVal instanceof Number n) amount = n.intValue();
            } else if (obj instanceof ConfigurationSection cs) {
                typeName = cs.getString("type");
                amount = cs.getInt("amount", 1);
            } else {
                continue;
            }

            if (typeName == null) continue;
            Material material = Material.matchMaterial(typeName);
            if (material == null) {
                plugin.getLogger().warning("[JobRewardManager] Unknown material: " + typeName);
                continue;
            }
            result.add(new ItemStack(material, amount));
        }
        return result;
    }

    private static List<PotionEffect> loadPotions(ConfigurationSection section, Plugin plugin) {
        List<PotionEffect> result = new ArrayList<>();
        List<?> raw = section.getList("potions");
        if (raw == null) return result;

        for (Object obj : raw) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            int duration = map.containsKey("duration") ? asInt(map.get("duration"), 600) : 600;

            if (map.containsKey("effect")) {
                // Single-effect entry
                PotionEffectType type = parseEffect(map.get("effect").toString(), plugin);
                if (type == null) continue;
                int amp = asInt(map.get("amplifier"), 0);
                result.add(new PotionEffect(type, duration, amp));

            } else if (map.containsKey("effects")) {
                // Multi-effect entry — each effect gets the same duration
                Object effectsObj = map.get("effects");
                if (!(effectsObj instanceof List<?> effectsList)) continue;
                for (Object effectObj : effectsList) {
                    if (!(effectObj instanceof Map<?, ?> effectMap)) continue;
                    Object typeVal = effectMap.get("type");
                    if (typeVal == null) continue;
                    PotionEffectType type = parseEffect(typeVal.toString(), plugin);
                    if (type == null) continue;
                    int amp = asInt(effectMap.get("amplifier"), 0);
                    result.add(new PotionEffect(type, duration, amp));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType parseEffect(String name, Plugin plugin) {
        // Try modern registry first, fall back to legacy name lookup
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase()));
        if (type == null) type = PotionEffectType.getByName(name);
        if (type == null) plugin.getLogger().warning("[JobRewardManager] Unknown potion effect: " + name);
        return type;
    }

    private static int asInt(Object val, int fallback) {
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String titleCase(String underscored) {
        String[] words = underscored.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
