package org.minecraft.atlas;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.minecraft.atlas.command.RandomTeleportCommand;
import org.minecraft.atlas.command.HomeCommand;
import org.minecraft.atlas.command.SetSpawnCommand;
import org.minecraft.atlas.command.HotelCommand;
import org.minecraft.atlas.command.SpawnCommand;
import org.minecraft.atlas.command.TpaCommand;
import org.minecraft.atlas.command.CrystalCommand;
import org.minecraft.atlas.command.DonjonCommand;
import org.minecraft.atlas.command.FactionCommand;
import org.minecraft.atlas.command.TagCommand;
import org.minecraft.atlas.donjon.DonjonManager;
import org.minecraft.atlas.donjon.ElectricalCreeperManager;
import org.minecraft.atlas.donjon.SmugglerManager;
import org.minecraft.atlas.donjon.RaiderPickaxe;
import org.minecraft.atlas.faction.RandomTeleportManager;
import org.minecraft.atlas.faction.DeathTeleportCooldownManager;
import org.minecraft.atlas.faction.HomeManager;
import org.minecraft.atlas.faction.HomeTeleportManager;
import org.minecraft.atlas.faction.SpawnManager;
import org.minecraft.atlas.faction.SpawnTeleportManager;
import org.minecraft.atlas.faction.TpaManager;
import org.minecraft.atlas.faction.CrystalGui;
import org.minecraft.atlas.faction.AtlasCrystalManager;
import org.minecraft.atlas.hotel.HotelShopManager;
import org.minecraft.atlas.hotel.Rubis;
import org.minecraft.atlas.listener.HotelListener;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionClaimManager;
import org.minecraft.atlas.faction.FactionLevelManager;
import org.minecraft.atlas.faction.FactionManager;
import org.minecraft.atlas.listener.ChatListener;
import org.minecraft.atlas.listener.DonjonListener;
import org.minecraft.atlas.listener.FactionListener;
import org.minecraft.atlas.listener.SpawnProtectionListener;
import org.minecraft.atlas.listener.TagListener;
import org.minecraft.atlas.command.JobCommand;
import org.minecraft.atlas.tag.TagManager;
import org.minecraft.atlas.job.JobManager;
import org.minecraft.atlas.job.JokeyriniManager;
import org.minecraft.atlas.listener.GuiListener;
import org.minecraft.atlas.listener.JobListener;
import org.minecraft.atlas.util.AfkManager;
import org.minecraft.atlas.util.ItemClearManager;

import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.atlas.listener.GolemListener;
import org.minecraft.atlas.listener.TradeListener;

public final class Atlas extends JavaPlugin {

    public static Atlas instance;

    public static YamlConfiguration factionsConfig;
    public static File factionsFile;

    public static YamlConfiguration donjonsConfig;
    public static File donjonsFile;

    public static YamlConfiguration jobsConfig;
    public static File jobsFile;

    public static YamlConfiguration tagsConfig;
    public static File tagsFile;

    public static YamlConfiguration hotelDataConfig;
    public static File hotelDataFile;

    @Override
    public void onEnable() {
        instance = this;

        // config.yml — homes, teleport cooldowns, crystal, spawn protection, tags
        saveDefaultConfig();
        FileConfiguration configFile = getConfig();

        // factions.yml — factions, claims, upgrades
        factionsFile = new File(getDataFolder(), "factions.yml");
        if (!factionsFile.exists()) saveResource("factions.yml", false);
        factionsConfig = YamlConfiguration.loadConfiguration(factionsFile);

        // donjons.yml — donjon system, instances, type configs
        donjonsFile = new File(getDataFolder(), "donjons.yml");
        if (!donjonsFile.exists()) saveResource("donjons.yml", false);
        donjonsConfig = YamlConfiguration.loadConfiguration(donjonsFile);

        // jobs.yml — quests, jokeyrini, job runtime data
        jobsFile = new File(getDataFolder(), "jobs.yml");
        if (!jobsFile.exists()) saveResource("jobs.yml", false);
        jobsConfig = YamlConfiguration.loadConfiguration(jobsFile);

        // tags.yml — in-world text display tags
        tagsFile = new File(getDataFolder(), "tags.yml");
        if (!tagsFile.exists()) saveResource("tags.yml", false);
        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);

        hotelDataFile = new File(getDataFolder(), "hotel-data.yml");
        hotelDataConfig = YamlConfiguration.loadConfiguration(hotelDataFile);

        // Load from config.yml
        AtlasCrystalManager.loadConfig(factionsConfig);
        SpawnProtectionListener.loadConfig(configFile);
        SpawnManager.loadConfig(configFile);
        RandomTeleportManager.loadConfig(configFile);
        SpawnTeleportManager.loadConfig(configFile);
        HomeManager.loadConfig(configFile);
        HomeTeleportManager.loadConfig(factionsConfig);
        TpaManager.loadConfig(configFile);
        AfkManager.loadConfig(configFile);
        DeathTeleportCooldownManager.loadConfig(configFile);

        // Load from factions.yml
        FactionLevelManager.loadUpgrades(factionsConfig);
        FactionManager.loadFactions(factionsConfig);
        FactionClaimManager.loadClaims(factionsConfig);

        // Load from jobs.yml
        JobManager.loadJobs(jobsConfig);
        JokeyriniManager.loadJokeyrini(jobsConfig);

        // Load from donjons.yml
        DonjonManager.loadConfig(donjonsConfig);
        DonjonManager.loadDonjons(donjonsConfig);
        SmugglerManager.loadConfig(donjonsConfig);

        ElectricalCreeperManager.init();
        SmugglerManager.init();
        RaiderPickaxe.init();
        Rubis.init();
        HotelShopManager.load(hotelDataConfig);
        TagManager.init();
        TagManager.loadTags(tagsConfig);
        HomeManager.loadHomes(configFile);

        // Register all listeners
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);
        getServer().getPluginManager().registerEvents(new JobListener(), this);
        getServer().getPluginManager().registerEvents(new DonjonListener(), this);
        getServer().getPluginManager().registerEvents(new TagListener(), this);
        getServer().getPluginManager().registerEvents(new CrystalGui(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new HotelListener(), this);
        AfkManager afkManager = new AfkManager();
        getServer().getPluginManager().registerEvents(afkManager, this);

        // Schedulers
        GolemListener.schedule(this);
        AtlasCrystalManager.schedule(this);
        DonjonManager.schedule(this);
        JobManager.scheduleExpiry(this);
        ItemClearManager.schedule(this);
        AfkManager.schedule(this);

        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(FactionCommand.build(), "Faction management commands", List.of("f"));
                        event.registrar().register(TradeCommand.build());
                        event.registrar().register(JobCommand.build());
                        event.registrar().register(CrystalCommand.build());
                        event.registrar().register(DonjonCommand.build());
                        event.registrar().register(TagCommand.build());
                        event.registrar().register(RandomTeleportCommand.build());
                        event.registrar().register(SpawnCommand.build());
                        event.registrar().register(SetSpawnCommand.build());
                        event.registrar().register(HotelCommand.build());
                        event.registrar().register(TpaCommand.build());
                        event.registrar().register(HomeCommand.buildSetHome());
                        event.registrar().register(HomeCommand.buildHome());
                        event.registrar().register(HomeCommand.buildDelHome());
                    }
                }
        );

        getLogger().info("Atlas enabled.");
    }

    @Override
    public void onDisable() {
        FileConfiguration configFile = getConfig();

        // Save to config.yml
        HomeManager.saveHomes(configFile);
        SpawnManager.saveSpawn(configFile);
        saveConfig();

        // Save to tags.yml
        TagManager.saveTags(tagsConfig);
        saveTagsConfig();

        // Save to factions.yml
        FactionManager.saveFactions(factionsConfig);
        FactionClaimManager.saveClaims(factionsConfig);
        AtlasCrystalManager.saveCrystalHomes(factionsConfig);
        saveFactionsConfig();

        // Save to jobs.yml
        JobManager.saveJobs(jobsConfig);
        JokeyriniManager.saveJokeyrini(jobsConfig);
        saveJobsConfig();

        // Save to donjons.yml
        DonjonManager.saveDonjonConfig(donjonsConfig);
        saveDonjonsConfig();
        // Save to hotel-data.yml
        HotelShopManager.save(hotelDataConfig);
        saveHotelDataConfig();

        getLogger().info("Atlas disabled.");
    }

    public static void saveTagsConfig() {
        try {
            tagsConfig.save(tagsFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save tags.yml: " + e.getMessage());
        }
    }

    public static void saveDonjonsConfig() {
        try {
            donjonsConfig.save(donjonsFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save donjons.yml: " + e.getMessage());
        }
    }

    public static void saveFactionsConfig() {
        try {
            factionsConfig.save(factionsFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save factions.yml: " + e.getMessage());
        }
    }

    public static void saveJobsConfig() {
        try {
            jobsConfig.save(jobsFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save jobs.yml: " + e.getMessage());
        }
    }

    public static void saveHotelDataConfig() {
        try {
            hotelDataConfig.save(hotelDataFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save hotel-data.yml: " + e.getMessage());
        }
    }
}
