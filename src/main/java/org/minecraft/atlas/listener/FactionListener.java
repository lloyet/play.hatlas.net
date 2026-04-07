package org.minecraft.atlas.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
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

public class FactionListener implements Listener {

    // -------------------------------------------------------------------------
    // Atlas Crystal item interaction
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInOffHand();
        if (!isAtlasCrystalItem(item)) return;

        event.setCancelled(true);

        String itemFaction = getCrystalItemFaction(item);
        if (itemFaction == null) return;

        String playerFactionName = FactionManager.getPlayerFaction(player.getUniqueId());

        // Player not in the crystal's faction: consume item, show message
        if (!itemFaction.equals(playerFactionName)) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            player.sendMessage(Component.text(
                    "This crystal doesn't belong to your faction and crumbled in your hands.",
                    NamedTextColor.RED));
            return;
        }

        // Player must be leader or owner
        Faction faction = FactionManager.getFaction(playerFactionName);
        boolean isOwner = faction.getOwner().equals(player.getUniqueId());
        boolean isLeader = !isOwner && faction.getRole(player.getUniqueId()) == FactionRole.LEADER;
        if (!isOwner && !isLeader) {
            player.sendMessage(Component.text(
                    "Only Leaders and Owners can place the Atlas Crystal.", NamedTextColor.RED));
            return;
        }

        // Only one atlas crystal per faction
        if (AtlasCrystalManager.getFactionCrystal(playerFactionName) != null) {
            player.sendMessage(Component.text(
                    "Your faction already has an Atlas Crystal.", NamedTextColor.RED));
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Spawn ender crystal 1 block above the clicked block surface
        Location crystalLoc = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawn(crystalLoc, EnderCrystal.class, crystal -> {
            crystal.setShowingBottom(false);
            AtlasCrystalManager.register(crystal, playerFactionName);
        });

        // Set faction home 1 block in the direction the player is facing
        Block homeBlock = block.getRelative(player.getFacing());
        Location home = homeBlock.getLocation().add(0.5, 0.0, 0.5);
        home.setYaw(player.getLocation().getYaw());
        home.setPitch(0);
        faction.setHome(home);

        // Consume the item
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        player.sendMessage(Component.text("Atlas Crystal placed! Faction home set.", NamedTextColor.GREEN));
        FactionManager.broadcastToFaction(playerFactionName,
                Component.text("The Atlas Crystal has been placed by " + player.getName() + "!",
                        NamedTextColor.GOLD),
                player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Atlas Crystal damage handling
    // -------------------------------------------------------------------------

    /**
     * Cancels all non-entity damage to atlas crystals (fire, explosions, etc.).
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        if (!AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent) return; // handled separately
        event.setCancelled(true);
    }

    /**
     * Applies our HP system when a player hits an atlas crystal.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onAtlasCrystalDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) return;
        AtlasCrystal atlasCrystal = AtlasCrystalManager.getCrystal(crystal.getUniqueId());
        if (atlasCrystal == null) return;

        // Always cancel vanilla behavior (no natural explosion)
        event.setCancelled(true);

        // Only players can damage atlas crystals
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        // Faction members cannot damage their own crystal
        String crystalFaction = atlasCrystal.getFactionName();
        if (crystalFaction.equals(FactionManager.getPlayerFaction(attacker.getUniqueId()))) return;

        double damage = event.getDamage();
        boolean died = atlasCrystal.damage(damage);

        // Persist updated HP
        crystal.getPersistentDataContainer()
                .set(AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING, crystalFaction);

        if (died) {
            AtlasCrystalManager.remove(crystal.getUniqueId());
            crystal.getWorld().createExplosion(crystal.getLocation(), 6.0f, true, true);
            crystal.remove();

            FactionManager.broadcastToFaction(crystalFaction,
                    Component.text("Your Atlas Crystal was destroyed by " + attacker.getName()
                            + "! The faction has been disbanded!", NamedTextColor.RED),
                    null);
            FactionManager.disbandFaction(crystalFaction);
        } else {
            attacker.sendMessage(Component.text(
                    "You dealt " + String.format("%.1f", damage) + " damage to "
                            + crystalFaction + "'s Atlas Crystal! ("
                            + (int) atlasCrystal.getHp() + "/" + (int) atlasCrystal.getMaxHp() + "♥)",
                    NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // Restore atlas crystals after server restart / chunk load
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof EnderCrystal crystal
                    && crystal.getPersistentDataContainer().has(
                    AtlasCrystalManager.getKeyFaction(), PersistentDataType.STRING)
                    && !AtlasCrystalManager.isAtlasCrystal(crystal.getUniqueId())) {
                AtlasCrystalManager.restore(crystal);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Faction chat prefix
    // -------------------------------------------------------------------------

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        if (factionName == null) return;
        Faction faction = FactionManager.getFaction(factionName);
        if (faction == null) return;

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text("[")
                        .append(Component.text(factionName, faction.getColor()))
                        .append(Component.text("] "))
                        .append(sourceDisplayName)
                        .append(Component.text(": "))
                        .append(message)
        );
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
