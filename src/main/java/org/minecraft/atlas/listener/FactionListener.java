package org.minecraft.atlas.listener;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.atlas.faction.AtlasCrystal;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.faction.FactionRole;
import org.minecraft.atlas.faction.HomeTeleportManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionListener implements Listener {

    // -------------------------------------------------------------------------
    // Message helpers
    // -------------------------------------------------------------------------

    private static Component error(String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    /**
     * Tracks last hit time (ms) per attacker to enforce 1-hit-per-second anti-spam.
     */
    private static final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Atlas Crystal item — placement
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAtlasCrystalItem(item)) return;

        event.setCancelled(true);

        String itemFaction = getCrystalItemFaction(item);
        if (itemFaction == null) return;

        String playerFactionName = FactionManager.getPlayerFaction(player.getUniqueId());

        // Not in the item's faction: show message, keep item
        if (!itemFaction.equals(playerFactionName)) {
            player.sendMessage(Component.text(
                    "This crystal doesn't belong to your faction.", NamedTextColor.RED));
            return;
        }

        // Must be leader or owner to place
        Faction faction = FactionManager.getFaction(playerFactionName);
        boolean isOwner = faction.getOwner().equals(player.getUniqueId());
        boolean isLeader = !isOwner && faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
        if (!isOwner && !isLeader) {
            player.sendMessage(Component.text(
                    "Only Leaders and Owners can place the Atlas Crystal.", NamedTextColor.RED));
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Spawn ender crystal 1 block above the clicked block surface
        EnderCrystal crystalEntity = block.getWorld().spawn(
                block.getLocation().add(0.5, 1.0, 0.5), EnderCrystal.class);
        crystalEntity.setShowingBottom(false);

        AtlasCrystal atlasCrystal = AtlasCrystalManager.register(crystalEntity, playerFactionName);

        // Home is 1 block in the direction the player is facing
        Location home = block.getRelative(player.getFacing()).getLocation().add(0.5, 0.0, 0.5);
        home.setYaw(player.getLocation().getYaw());
        home.setPitch(0);
        atlasCrystal.setHome(home);
        AtlasCrystalManager.saveHome(atlasCrystal);

        // Consume the item
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        // Start naming flow via Minecraft dialog
        AtlasCrystalManager.setPendingNaming(player.getUniqueId(), atlasCrystal);
        openNamingDialog(player, atlasCrystal, null);
    }

    // -------------------------------------------------------------------------
    // Crystal naming via Minecraft dialog
    // -------------------------------------------------------------------------

    private static void openNamingDialog(Player player, AtlasCrystal pending, String errorMsg) {
        List<DialogBody> body = new ArrayList<>();
        if (errorMsg != null) {
            body.add(DialogBody.plainMessage(Component.text(errorMsg, NamedTextColor.RED)));
        }
        body.add(DialogBody.plainMessage(
                Component.text("Enter a unique name (single word, no spaces).", NamedTextColor.GRAY)));

        DialogBase base = DialogBase.builder(Component.text("Name Your Atlas Crystal", NamedTextColor.GOLD))
                .canCloseWithEscape(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body)
                .inputs(List.of(
                        DialogInput.text("name", Component.text("Crystal Name"))
                                .maxLength(32)
                                .initial("")
                                .labelVisible(true)
                                .build()
                ))
                .build();

        ActionButton submitButton = ActionButton.builder(Component.text("Confirm", NamedTextColor.GREEN))
                .width(200)
                .action(DialogAction.customClick((response, audience) -> {
                    String name = response.getText("name");
                    if (name == null || name.isBlank()) {
                        openNamingDialog(player, pending, "Name cannot be empty.");
                        return;
                    }
                    if (name.contains(" ")) {
                        openNamingDialog(player, pending, "Name cannot contain spaces.");
                        return;
                    }
                    if (AtlasCrystalManager.hasCrystalWithName(pending.getFactionName(), name)) {
                        openNamingDialog(player, pending, "'" + name + "' is already taken.");
                        return;
                    }
                    AtlasCrystalManager.clearPendingNaming(player.getUniqueId());
                    AtlasCrystalManager.assignName(pending, name.trim());
                    player.sendMessage(Component.text(
                            "Atlas Crystal named '" + name + "'!", NamedTextColor.GREEN));
                    FactionManager.broadcastToFaction(pending.getFactionName(),
                            Component.text("Atlas Crystal '" + name + "' has been placed by "
                                    + player.getName() + "!", NamedTextColor.GOLD),
                            player.getUniqueId());
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();

        Dialog dialog = Dialog.create(factory ->
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(List.of(submitButton), null, 1))
        );

        player.showDialog(dialog);
    }

    /**
     * If a player disconnects mid-naming, assign a generated fallback name.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AtlasCrystal pending = AtlasCrystalManager.getPendingNaming(player.getUniqueId());
        if (pending == null) return;

        AtlasCrystalManager.clearPendingNaming(player.getUniqueId());
        String fallback = "Crystal_" + pending.getEntity().getUniqueId().toString().substring(0, 6);
        String candidate = fallback;
        int i = 1;
        while (AtlasCrystalManager.hasCrystalWithName(pending.getFactionName(), candidate)) {
            candidate = fallback + "_" + i++;
        }
        AtlasCrystalManager.assignName(pending, candidate);
    }

    // -------------------------------------------------------------------------
    // Atlas Crystal damage handling
    // -------------------------------------------------------------------------

    /**
     * Cancels an active /faction home countdown if the player takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamagedCancelTeleport(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (HomeTeleportManager.cancelTeleport(player.getUniqueId())) {
            player.sendActionBar(Component.text("Teleport cancelled! (took damage)", NamedTextColor.RED));
            player.sendMessage(Component.text(
                    "Teleport to faction home cancelled because you took damage.", NamedTextColor.RED));
        }
    }

    /** Cancels all non-entity damage to atlas crystals (fire, explosions, etc.). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent) return; // handled separately
        event.setCancelled(true);
    }

    /** Applies our HP system when a player hits an atlas crystal. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAtlasCrystalDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        AtlasCrystal atlasCrystal = AtlasCrystalManager.getCrystal(crystal.getUniqueId());
        if (atlasCrystal == null) return;

        event.setCancelled(true);

        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        // Players with no faction cannot damage atlas crystals
        String attackerFaction = FactionManager.getPlayerFaction(attacker.getUniqueId());
        if (attackerFaction == null) {
            attacker.sendMessage(Component.text(
                    "You must be in a faction to attack an Atlas Crystal.", NamedTextColor.RED));
            return;
        }

        // Faction members cannot damage their own crystal
        String crystalFaction = atlasCrystal.getFactionName();
        if (crystalFaction.equals(attackerFaction)) return;

        // Anti-spam: allow at most 1 hit per second per attacker
        long now = System.currentTimeMillis();
        Long last = lastHitTime.get(attacker.getUniqueId());
        if (last != null && now - last < 1000L) return;
        lastHitTime.put(attacker.getUniqueId(), now);

        double damage = event.getDamage();
        boolean died = atlasCrystal.damage(damage);

        crystal.getPersistentDataContainer()
                .set(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING, crystalFaction);

        // Play elder guardian hurt sound at the crystal location
        crystal.getWorld().playSound(
                crystal.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 1.0f, 1.0f);

        // Particle burst at crystal location (visible to all nearby players)
        org.bukkit.Location loc = crystal.getLocation();
        crystal.getWorld().spawnParticle(Particle.CRIT, loc, 30, 0.4, 0.4, 0.4, 0.25);
        crystal.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 10, 0.3, 0.3, 0.3, 0.0);
        crystal.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 20, 0.5, 0.5, 0.5, 0.1);
        crystal.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
        crystal.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 8, 0.3, 0.3, 0.3, 0.02);
        crystal.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.2, 0.2, 0.0);

        // Show damage dealt in bold red to the attacker only
        attacker.sendActionBar(Component.text("-" + (int) damage, NamedTextColor.RED)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

        if (died) {
            String crystalName = atlasCrystal.getName();
            String displayName = (crystalName != null && !crystalName.isEmpty()) ? crystalName : "(unnamed)";
            AtlasCrystalManager.remove(crystal.getUniqueId());
            // Play beacon deactivate sound before explosion
            crystal.getWorld().playSound(
                    crystal.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.0f, 1.0f);
            crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true);
            crystal.remove();
            FactionManager.broadcastToFaction(crystalFaction,
                    error("The faction has been disbanded by enemy players"), null);
            FactionManager.disbandFaction(crystalFaction);
        }
    }

    // -------------------------------------------------------------------------
    // Restore atlas crystals after chunk load / server restart
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderCrystal crystal
                    && crystal.getPersistentDataContainer()
                    .has(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING)
                    && !AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) {
                AtlasCrystalManager.restore(crystal);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isAtlasCrystalItem(ItemStack item) {
        if (item == null || item.getType() != Material.END_CRYSTAL) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING);
    }

    private static String getCrystalItemFaction(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer()
                .get(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING);
    }
}
