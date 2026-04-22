package org.minecraft.atlas.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        boolean isOp = player.isOp();

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component nameComponent = isOp
                    ? Component.text(player.getName(), NamedTextColor.DARK_RED)
                    : sourceDisplayName;

            if (faction == null) {
                return nameComponent.append(Component.text(": ")).append(message);
            }
            return Component.text("[" + factionName + "] ", faction.getColor())
                    .append(nameComponent)
                    .append(Component.text(": "))
                    .append(message);
        });
    }
}
