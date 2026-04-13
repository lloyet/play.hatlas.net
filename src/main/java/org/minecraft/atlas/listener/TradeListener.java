package org.minecraft.atlas.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.trade.Trade;
import org.minecraft.atlas.trade.TradeManager;

public class TradeListener implements Listener {

    private static final long REQUEST_EXPIRY_TICKS = 20L * 60; // 60 seconds

    // -------------------------------------------------------------------------
    // Right-click on a player → send trade request
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only main hand, only when right-clicking another player
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player initiator = event.getPlayer();
        if (initiator.equals(target)) return;

        if (TradeManager.hasTrade(initiator.getUniqueId())) {
            initiator.sendMessage(Component.text("You are already in a trade.", NamedTextColor.RED));
            return;
        }
        if (TradeManager.hasTrade(target.getUniqueId())) {
            initiator.sendMessage(Component.text(target.getName() + " is already in a trade.", NamedTextColor.RED));
            return;
        }

        // Prevent spamming duplicate requests
        if (TradeManager.hasPendingRequest(target.getUniqueId())
                && initiator.getUniqueId().equals(TradeManager.getPendingInitiator(target.getUniqueId()))) {
            initiator.sendMessage(Component.text(
                    "You already sent a trade request to " + target.getName() + ".", NamedTextColor.GOLD));
            return;
        }

        TradeManager.addPendingRequest(target.getUniqueId(), initiator.getUniqueId());

        initiator.sendMessage(Component.text(
                "Trade request sent to " + target.getName() + ".", NamedTextColor.GOLD));

        target.sendMessage(
                Component.text(initiator.getName() + " wants to trade with you.  ", NamedTextColor.GOLD)
                        .append(Component.text("[Accept]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/trade accept " + initiator.getName())))
                        .append(Component.text("  "))
                        .append(Component.text("[Decline]", NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/trade decline " + initiator.getName())))
        );

        // Auto-expire the request after 60 s
        Bukkit.getScheduler().runTaskLater(Atlas.instance, () -> {
            if (TradeManager.hasPendingRequest(target.getUniqueId())
                    && initiator.getUniqueId().equals(TradeManager.getPendingInitiator(target.getUniqueId()))) {
                TradeManager.removePendingRequest(target.getUniqueId());
                if (initiator.isOnline()) initiator.sendMessage(Component.text(
                        "Trade request to " + target.getName() + " expired.", NamedTextColor.GRAY));
                if (target.isOnline()) target.sendMessage(Component.text(
                        "Trade request from " + initiator.getName() + " expired.", NamedTextColor.GRAY));
            }
        }, REQUEST_EXPIRY_TICKS);
    }

    // -------------------------------------------------------------------------
    // Inventory click – all item movement logic is handled manually
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Trade.Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Trade trade = holder.getTrade();
        if (trade.isFinished()) return;

        boolean isInitiator = holder.isInitiator();
        int rawSlot = event.getRawSlot();

        // ── Click in the player's own inventory (bottom half, rawSlot >= 54) ──
        if (rawSlot >= 54) {
            // Only allow shift-click to move items from player inventory into left slots
            if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return;
            if (isAccepted(trade, isInitiator)) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            Inventory tradeInv = getPlayerInv(trade, isInitiator);
            for (int leftSlot : Trade.LEFT_SLOTS) {
                ItemStack existing = tradeInv.getItem(leftSlot);
                if (existing == null || existing.getType().isAir()) {
                    tradeInv.setItem(leftSlot, clicked.clone());
                    event.setCurrentItem(null);
                    trade.onItemChanged();
                    trade.sync();
                    return;
                }
            }
            return; // No space in trade inventory
        }

        // ── Click in the trade inventory (top half, rawSlot 0-53) ──
        if (rawSlot == Trade.CANCEL_SLOT) { trade.cancel(player); return; }
        if (rawSlot == Trade.ACCEPT_SLOT) { trade.toggleAccept(isInitiator); return; }
        if (Trade.isGlassSlot(rawSlot))   return; // separator – ignore
        if (Trade.isRightSlot(rawSlot))   return; // read-only mirror – ignore

        if (!Trade.isLeftSlot(rawSlot)) return;
        if (isAccepted(trade, isInitiator)) return; // locked after accepting

        Inventory inv = getPlayerInv(trade, isInitiator);
        handleLeftSlotClick(event, inv, rawSlot, player);
        trade.onItemChanged();
        trade.sync();
    }

    // ── Manual item-movement logic for a left-side slot ──

    private void handleLeftSlotClick(InventoryClickEvent event, Inventory inv, int slot, Player player) {
        ClickType click   = event.getClick();
        ItemStack cursor  = player.getItemOnCursor();
        ItemStack current = inv.getItem(slot);

        boolean cursorEmpty  = cursor.getType().isAir();
        boolean currentEmpty = current == null || current.getType().isAir();

        switch (click) {
            case LEFT -> {
                if (cursorEmpty && currentEmpty) return;
                if (cursorEmpty) {
                    // Pick up entire stack
                    player.setItemOnCursor(current.clone());
                    inv.setItem(slot, null);
                } else if (currentEmpty) {
                    // Place entire cursor stack
                    inv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(null);
                } else if (current.isSimilar(cursor)) {
                    // Merge stacks
                    int space = current.getMaxStackSize() - current.getAmount();
                    int add   = Math.min(space, cursor.getAmount());
                    if (add > 0) {
                        ItemStack merged = current.clone();
                        merged.setAmount(current.getAmount() + add);
                        inv.setItem(slot, merged);
                        ItemStack remaining = cursor.clone();
                        remaining.setAmount(cursor.getAmount() - add);
                        player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                    }
                } else {
                    // Swap
                    inv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(current.clone());
                }
            }
            case RIGHT -> {
                if (cursorEmpty && currentEmpty) return;
                if (cursorEmpty) {
                    // Pick up half
                    int half = (int) Math.ceil(current.getAmount() / 2.0);
                    ItemStack picked = current.clone(); picked.setAmount(half);
                    ItemStack left   = current.clone(); left.setAmount(current.getAmount() - half);
                    player.setItemOnCursor(picked);
                    inv.setItem(slot, left.getAmount() == 0 ? null : left);
                } else if (currentEmpty) {
                    // Place one
                    ItemStack placed = cursor.clone(); placed.setAmount(1);
                    inv.setItem(slot, placed);
                    ItemStack remaining = cursor.clone(); remaining.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                } else if (current.isSimilar(cursor) && current.getAmount() < current.getMaxStackSize()) {
                    // Add one to stack
                    ItemStack grown = current.clone(); grown.setAmount(current.getAmount() + 1);
                    inv.setItem(slot, grown);
                    ItemStack remaining = cursor.clone(); remaining.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(remaining.getAmount() == 0 ? null : remaining);
                } else if (!current.isSimilar(cursor)) {
                    // Swap
                    inv.setItem(slot, cursor.clone());
                    player.setItemOnCursor(current.clone());
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                // Move item from left slot back to player inventory
                if (!currentEmpty) {
                    player.getInventory().addItem(current.clone()).values()
                            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    inv.setItem(slot, null);
                }
            }
            default -> { /* cancel number keys, drop, etc. */ }
        }
    }

    // -------------------------------------------------------------------------
    // Drag – cancel to keep full control
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Trade.Holder) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Inventory close – cancel trade if not yet finished
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Trade.Holder holder)) return;
        Trade trade = holder.getTrade();
        if (!trade.isFinished()) {
            trade.cancel((Player) event.getPlayer());
        }
    }

    // -------------------------------------------------------------------------
    // Player quits or dies mid-trade
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelTradeFor(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cancelTradeFor(event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private void cancelTradeFor(Player player) {
        Trade trade = TradeManager.getTrade(player.getUniqueId());
        if (trade != null && !trade.isFinished()) trade.cancel(player);
    }

    private static boolean isAccepted(Trade trade, boolean isInitiator) {
        return isInitiator ? trade.isInitiatorAccepted() : trade.isTargetAccepted();
    }

    private static Inventory getPlayerInv(Trade trade, boolean isInitiator) {
        return isInitiator ? trade.getInitiatorInv() : trade.getTargetInv();
    }
}
