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
import org.minecraft.atlas.command.ProtectionCommand;
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
import org.minecraft.atlas.teleport.RandomTeleportManager;
import org.minecraft.atlas.teleport.DeathTeleportCooldownManager;
import org.minecraft.atlas.teleport.HomeManager;
import org.minecraft.atlas.teleport.HomeTeleportManager;
import org.minecraft.atlas.spawn.ProtectionManager;
import org.minecraft.atlas.spawn.SpawnManager;
import org.minecraft.atlas.spawn.SpawnTeleportManager;
import org.minecraft.atlas.teleport.TeleportAtManager;
import org.minecraft.atlas.listener.CrystalListener;
import org.minecraft.atlas.crystal.AtlasCrystalManager;
import org.minecraft.atlas.command.TradeCommand;
import org.minecraft.atlas.faction.FactionClaimBorderRenderer;
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
import org.minecraft.atlas.quest.QuestCommand;
import org.minecraft.atlas.quest.QuestManager;
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

    public static YamlConfiguration tagsDataConfig;
    public static File tagsDataFile;

    public static YamlConfiguration factionsDataConfig;
    public static File factionsDataFile;

    public static YamlConfiguration donjonsDataConfig;
    public static File donjonsDataFile;

    public static YamlConfiguration jobsDataConfig;
    public static File jobsDataFile;

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

        // tags-data.yml — in-world text display tags (runtime-only, not in resources)
        tagsDataFile = new File(getDataFolder(), "tags-data.yml");
        tagsDataConfig = YamlConfiguration.loadConfiguration(tagsDataFile);

        // Data files (runtime-only, not shipped in resources)
        factionsDataFile = new File(getDataFolder(), "factions-data.yml");
        factionsDataConfig = YamlConfiguration.loadConfiguration(factionsDataFile);

        donjonsDataFile = new File(getDataFolder(), "donjons-data.yml");
        donjonsDataConfig = YamlConfiguration.loadConfiguration(donjonsDataFile);

        jobsDataFile = new File(getDataFolder(), "jobs-data.yml");
        jobsDataConfig = YamlConfiguration.loadConfiguration(jobsDataFile);

        // Load from config.yml
        AtlasCrystalManager.loadConfig(factionsConfig);
        ProtectionManager.loadConfig(configFile);
        SpawnManager.loadConfig(configFile);
        RandomTeleportManager.loadConfig(configFile);
        SpawnTeleportManager.loadConfig(configFile);
        HomeManager.loadConfig(configFile);
        HomeTeleportManager.loadConfig(factionsConfig);
        TeleportAtManager.loadConfig(configFile);
        AfkManager.loadConfig(configFile);
        DeathTeleportCooldownManager.loadConfig(configFile);

        // Load from factions.yml (config) and factions-data.yml (data)
        FactionLevelManager.loadUpgrades(factionsConfig);
        FactionManager.loadFactions(factionsDataConfig);
        FactionClaimManager.loadClaims(factionsDataConfig);
        AtlasCrystalManager.loadCrystalHomes(factionsDataConfig);

        // Load from jobs.yml (config) and jobs-data.yml (data)
        QuestManager.loadQuestConfig(jobsConfig);
        JobManager.loadJobData(jobsDataConfig);
        JokeyriniManager.loadJokeyriniConfig(jobsConfig);
        JokeyriniManager.loadJokeyriniData(jobsDataConfig);

        // Load from donjons.yml (config) and donjons-data.yml (data)
        DonjonManager.loadConfig(donjonsConfig);
        DonjonManager.loadDonjons(donjonsDataConfig);
        SmugglerManager.loadConfig(donjonsConfig);

        ElectricalCreeperManager.init();
        SmugglerManager.init();
        RaiderPickaxe.init();
        TagManager.init();
        TagManager.loadTags(tagsDataConfig);
        HomeManager.loadHomes(configFile);

        // Register all listeners
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new FactionListener(), this);
        getServer().getPluginManager().registerEvents(new TradeListener(), this);
        getServer().getPluginManager().registerEvents(new GolemListener(), this);
        getServer().getPluginManager().registerEvents(new JobListener(), this);
        getServer().getPluginManager().registerEvents(new DonjonListener(), this);
        getServer().getPluginManager().registerEvents(new TagListener(), this);
        getServer().getPluginManager().registerEvents(new CrystalListener(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(), this);
        AfkManager afkManager = new AfkManager();
        getServer().getPluginManager().registerEvents(afkManager, this);

        // Schedulers
        GolemListener.schedule(this);
        AtlasCrystalManager.schedule(this);
        DonjonManager.schedule(this);
        QuestManager.scheduleExpiry(this);
        ItemClearManager.schedule(this);
        AfkManager.schedule(this);
        FactionClaimBorderRenderer.schedule(this);

        // Register all commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                new LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>() {
                    @Override
                    public void run(@NonNull ReloadableRegistrarEvent<Commands> event) {
                        event.registrar().register(FactionCommand.build(), "Faction management commands", List.of("f"));
                        event.registrar().register(TradeCommand.build());
                        event.registrar().register(JobCommand.build());
                        event.registrar().register(QuestCommand.build());
                        event.registrar().register(CrystalCommand.build());
                        event.registrar().register(DonjonCommand.build());
                        event.registrar().register(TagCommand.build());
                        event.registrar().register(RandomTeleportCommand.build());
                        event.registrar().register(SpawnCommand.build());
                        event.registrar().register(SetSpawnCommand.build());
                        event.registrar().register(ProtectionCommand.build());
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
        ProtectionManager.saveConfig(configFile);
        saveConfig();

        // Save to tags-data.yml
        TagManager.saveTags(tagsDataConfig);
        saveTagsDataConfig();

        // Save to factions-data.yml
        FactionManager.saveFactions(factionsDataConfig);
        FactionClaimManager.saveClaims(factionsDataConfig);
        AtlasCrystalManager.saveCrystalHomes(factionsDataConfig);
        saveFactionsDataConfig();

        // Save to jobs-data.yml
        JobManager.saveJobData(jobsDataConfig);
        JokeyriniManager.saveJokeyriniData(jobsDataConfig);
        saveJobsDataConfig();

        // Save to donjons-data.yml
        DonjonManager.saveDonjonData(donjonsDataConfig);
        saveDonjonsDataConfig();

        getLogger().info("Atlas disabled.");
    }

    public static void saveTagsDataConfig() {
        try {
            tagsDataConfig.save(tagsDataFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save tags-data.yml: " + e.getMessage());
        }
    }

    public static void saveDonjonsDataConfig() {
        try {
            donjonsDataConfig.save(donjonsDataFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save donjons-data.yml: " + e.getMessage());
        }
    }

    public static void saveFactionsDataConfig() {
        try {
            factionsDataConfig.save(factionsDataFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save factions-data.yml: " + e.getMessage());
        }
    }

    public static void saveJobsDataConfig() {
        try {
            jobsDataConfig.save(jobsDataFile);
        } catch (IOException e) {
            instance.getLogger().severe("Could not save jobs-data.yml: " + e.getMessage());
        }
    }
}
