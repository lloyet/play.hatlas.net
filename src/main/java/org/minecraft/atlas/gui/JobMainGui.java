package org.minecraft.atlas.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.minecraft.atlas.Atlas;
import org.minecraft.atlas.job.ActiveQuest;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.PlayerJobData;
import org.minecraft.atlas.util.GuiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JobMainGui implements AtlasGui {

    private static final int SLOT_MY_QUESTS    = 11;
    private static final int SLOT_DAILY_QUESTS = 15;
    private static final int SLOT_BACK         = 26;

    private final UUID playerUUID;
    private final Inventory inventory;

    public JobMainGui(Player player) {
        this.playerUUID = player.getUniqueId();

        PlayerJobData data = JobManager.getJobData(player.getUniqueId());
        String title = data != null ? data.getJob().getDisplayName() + " - Quests" : "Job Menu";

        this.inventory = Atlas.instance.getServer().createInventory(this, 27,
                Component.text(title, NamedTextColor.DARK_AQUA));

        // My Quests item
        ItemStack myQuests = new ItemStack(Material.PAPER);
        ItemMeta mqMeta = myQuests.getItemMeta();
        mqMeta.displayName(Component.text("My Quests", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<ActiveQuest> active = JobManager.getActiveQuests(player.getUniqueId());
        List<Component> mqLore = new ArrayList<>();
        if (active.isEmpty()) {
            mqLore.add(Component.text("No active quests.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            mqLore.add(Component.text(active.size() + "/2 active quests.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        mqLore.add(Component.text("Click to view.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        mqMeta.lore(mqLore);
        myQuests.setItemMeta(mqMeta);
        this.inventory.setItem(SLOT_MY_QUESTS, myQuests);

        // Daily Quests item
        ItemStack daily = new ItemStack(Material.CLOCK);
        ItemMeta dMeta = daily.getItemMeta();
        dMeta.displayName(Component.text("Daily Quests", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> dLore = new ArrayList<>();
        if (JobManager.isDailyLocked(player.getUniqueId())) {
            dLore.add(Component.text("You have chosen your 2 daily quests.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text("Come back tomorrow!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            int selected = JobManager.getDailySelectedCount(player.getUniqueId());
            dLore.add(Component.text("Select up to 2 quests per day.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text(selected + "/2 selected today.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text("Click to choose.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        dMeta.lore(dLore);
        daily.setItemMeta(dMeta);
        this.inventory.setItem(SLOT_DAILY_QUESTS, daily);

        this.inventory.setItem(SLOT_BACK, GuiUtil.buildBackItem("Close"));
        GuiUtil.fillGray(this.inventory);
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_MY_QUESTS) {
            new JobQuestListGui(player).open(player);
            return;
        }

        if (slot == SLOT_DAILY_QUESTS) {
            if (JobManager.isDailyLocked(player.getUniqueId())) {
                player.sendMessage(Component.text(
                        "You have already selected your 2 daily quests. Come back tomorrow!", NamedTextColor.RED));
                return;
            }
            new JobDailyGui(player).open(player);
        }
    }
}
