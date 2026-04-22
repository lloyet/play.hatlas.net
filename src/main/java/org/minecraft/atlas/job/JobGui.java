package org.minecraft.atlas.job;

import org.bukkit.entity.Player;
import org.minecraft.atlas.gui.JobMainHolder;

public class JobGui {

    /** Opens the NPC job main menu. */
    public static void openJobMain(Player player) {
        new JobMainHolder(player).open(player);
    }
}
