package org.minecraft.atlas.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.minecraft.atlas.faction.Faction;
import org.minecraft.atlas.faction.FactionManager;

public class ChatListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String factionName = FactionManager.getPlayerFaction(player.getUniqueId());
        Faction faction = factionName != null ? FactionManager.getFaction(factionName) : null;

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            if (faction == null) {
                return sourceDisplayName.append(Component.text(": ")).append(message);
            }
            return Component.text("[" + factionName + "] ", faction.getColor())
                    .append(sourceDisplayName)
                    .append(Component.text(": "))
                    .append(message);
        });
    }
}
