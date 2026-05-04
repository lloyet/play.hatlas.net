package org.minecraft.atlas.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.minecraft.atlas.Atlas;

public class SpawnManager {

    private static Location customSpawn = null;

    public static void loadConfig(FileConfiguration config) {
        if (!config.contains("spawn.world")) return;
        String worldName = config.getString("spawn.world");
        assert worldName != null;
        World world = Atlas.instance.getServer().getWorld(worldName);
        if (world == null) return;
        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");
        customSpawn = new Location(world, x, y, z, yaw, pitch);
    }

    public static void saveSpawn(FileConfiguration config) {
        if (customSpawn == null) return;
        config.set("spawn.world", customSpawn.getWorld().getName());
        config.set("spawn.x", customSpawn.getX());
        config.set("spawn.y", customSpawn.getY());
        config.set("spawn.z", customSpawn.getZ());
        config.set("spawn.yaw", (double) customSpawn.getYaw());
        config.set("spawn.pitch", (double) customSpawn.getPitch());
    }

    public static Location getSpawn(World world) {
        return customSpawn != null ? customSpawn : world.getSpawnLocation();
    }

    public static void setSpawn(Location location) {
        customSpawn = location.clone();
    }
}
