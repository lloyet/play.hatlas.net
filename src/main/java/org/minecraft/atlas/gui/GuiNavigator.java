package org.minecraft.atlas.gui;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuiNavigator {

    private static final Map<UUID, Deque<AtlasGui>> stacks = new ConcurrentHashMap<>();

    public static void push(UUID playerUUID, AtlasGui gui) {
        stacks.computeIfAbsent(playerUUID, k -> new ArrayDeque<>()).push(gui);
    }

    public static void back(Player player) {
        Deque<AtlasGui> stack = stacks.get(player.getUniqueId());
        if (stack == null || stack.isEmpty()) {
            player.closeInventory();
        } else {
            player.openInventory(stack.pop().getInventory());
        }
    }

    public static void clear(UUID playerUUID) {
        stacks.remove(playerUUID);
    }
}
