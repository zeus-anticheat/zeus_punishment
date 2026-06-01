package org.vennv.zeuspunishment.gateway;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.ZeusPunishmentEngine;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;
import org.vennv.zeuspunishment.gateway.commands.ZPunishCommand;

public class ZeusPunishmentPlugin extends JavaPlugin {

    private PunishmentConfig config;
    private PunishmentDispatcher dispatcher;
    private ZeusApiClient apiClient;
    private BanwaveManager banwaveManager;
    private ZeusPunishmentEngine engine;
    private int tickTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Bukkit's config.yml
        loadPluginConfig();

        this.dispatcher = new BukkitDispatcher(this);
        this.apiClient = new ZeusApiClient(config.getEndpointUrl(), config.getConnectTimeoutMs(), config.getReadTimeoutMs(), config.getReconnectInitialMs(), config.getReconnectMaxMs());
        this.banwaveManager = new BanwaveManager(config, dispatcher);
        this.engine = new ZeusPunishmentEngine(config, dispatcher, apiClient, banwaveManager);

        // Register Command
        getCommand("zpunish").setExecutor(new ZPunishCommand(this));

        // Register Listeners
        Bukkit.getPluginManager().registerEvents(new org.vennv.zeuspunishment.gateway.gui.MenuListener(this), this);

        // Start Scheduler
        startSchedulers();

        getLogger().info("ZeusPunishmentGateway Enabled!");
    }

    @Override
    public void onDisable() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
        }
        if (engine != null) {
            engine.stop();
        }
        getLogger().info("ZeusPunishmentGateway Disabled!");
    }

    private void loadPluginConfig() {
        reloadConfig();
        this.config = new PunishmentConfig();
        // Here we would parse Bukkit's FileConfiguration into PunishmentConfig.
        // For brevity, using defaults with Endpoint from config if exists.
        if (getConfig().contains("endpoint")) {
            config.setEndpointUrl(getConfig().getString("endpoint"));
        }
        config.setDevVerboseMode(getConfig().getBoolean("verbose", false));
        config.setDevMode(getConfig().getBoolean("dev_mode", false));
        if (getConfig().contains("message_kick")) {
            config.setMessageKick(getConfig().getString("message_kick"));
        }
        if (getConfig().contains("message_ban")) {
            config.setMessageBan(getConfig().getString("message_ban"));
        }
        config.setBanwaveEnabled(getConfig().getBoolean("banwave.enabled", false));
        config.setEffectsEnabled(getConfig().getBoolean("effects_enabled", true));
        config.setConnectTimeoutMs(clampNetworkMs(getConfig().getInt("network.connect_timeout_ms", config.getConnectTimeoutMs()), 1000, 60000, config.getConnectTimeoutMs()));
        config.setReadTimeoutMs(clampNetworkMs(getConfig().getInt("network.read_timeout_ms", config.getReadTimeoutMs()), 1000, 60000, config.getReadTimeoutMs()));
        config.setReconnectInitialMs(clampNetworkMs(getConfig().getInt("network.reconnect_initial_ms", config.getReconnectInitialMs()), 250, 60000, config.getReconnectInitialMs()));
        config.setReconnectMaxMs(clampNetworkMs(getConfig().getInt("network.reconnect_max_ms", config.getReconnectMaxMs()), config.getReconnectInitialMs(), 300000, config.getReconnectMaxMs()));
        config.setHealthRetries(clampNetworkMs(getConfig().getInt("network.health_retries", config.getHealthRetries()), 1, 20, config.getHealthRetries()));

        // Load Model Rules
        org.bukkit.configuration.ConfigurationSection modelSec = getConfig().getConfigurationSection("models");
        if (modelSec != null) {
            for (String modelId : modelSec.getKeys(false)) {
                String warnAction = modelSec.getString(modelId + ".warning_action", "NONE");
                String kickAction = modelSec.getString(modelId + ".kick_action", "NONE");
                String banAction = modelSec.getString(modelId + ".ban_action", "NONE");

                config.setActionForModel(modelId, org.vennv.zeuspunishment.core.model.Severity.WARNING, org.vennv.zeuspunishment.core.config.ActionType.fromString(warnAction));
                config.setActionForModel(modelId, org.vennv.zeuspunishment.core.model.Severity.KICK, org.vennv.zeuspunishment.core.config.ActionType.fromString(kickAction));
                config.setActionForModel(modelId, org.vennv.zeuspunishment.core.model.Severity.BAN, org.vennv.zeuspunishment.core.config.ActionType.fromString(banAction));
            }
        }
    }

    private int clampNetworkMs(int value, int min, int max, int fallback) {
        if (value < min) return fallback;
        return Math.min(value, max);
    }

    public void reloadPlugin() {
        java.util.List<String> oldModels = this.engine != null ? this.engine.getCachedModels() : new java.util.ArrayList<>();
        if (this.engine != null) {
            this.engine.stop();
        }
        loadPluginConfig();
        this.apiClient = new ZeusApiClient(config.getEndpointUrl(), config.getConnectTimeoutMs(), config.getReadTimeoutMs(), config.getReconnectInitialMs(), config.getReconnectMaxMs());
        this.banwaveManager = new BanwaveManager(config, dispatcher);
        // Reload engine with new config
        this.engine = new ZeusPunishmentEngine(config, dispatcher, apiClient, banwaveManager);
        this.engine.setCachedModels(oldModels);
        // Start engine streams again
        this.engine.start();
    }

    private void startSchedulers() {
        if (tickTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tickTaskId);
        }
        // Run banwave tick every 1 second (20 ticks) synchronously for safe broadcasts
        tickTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            banwaveManager.tickSecond();
        }, 20L, 20L).getTaskId();

        // Start real-time SSE stream background listener
        engine.start();
    }

    public PunishmentConfig getCoreConfig() { return config; }
    public BanwaveManager getBanwaveManager() { return banwaveManager; }
    public ZeusPunishmentEngine getEngine() { return engine; }
}
