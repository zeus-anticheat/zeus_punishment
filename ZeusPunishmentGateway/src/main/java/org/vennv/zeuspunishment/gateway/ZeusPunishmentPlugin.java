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
        this.apiClient = new ZeusApiClient(config.getEndpointUrl());
        org.vennv.zeuspunishment.core.audit.AuditSink auditSink = ZeusPunishmentEngine.createDefaultAuditSink(getDataFolder().toPath(), getLogger(), config.isAuditEnabled());
        org.vennv.zeuspunishment.core.cooldown.CooldownGate cooldownGate = new org.vennv.zeuspunishment.core.cooldown.CooldownGate();
        this.banwaveManager = new BanwaveManager(config, dispatcher, auditSink, cooldownGate);
        this.engine = new ZeusPunishmentEngine(config, dispatcher, apiClient, banwaveManager, auditSink, cooldownGate);

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
        config.setDryRun(getConfig().getBoolean("policy.dry_run", true));
        config.setEnforcementEnabled(getConfig().getBoolean("policy.enforcement_enabled", false));
        config.setPolicyPreset(getConfig().getString("policy.preset", "observe"));
        config.setManualBanwaveApprovalRequired(getConfig().getBoolean("policy.manual_banwave_approval", true));
        config.setImmediateBanEnabled(getConfig().getBoolean("policy.high_impact.immediate_ban", false));
        config.setBroadcastsEnabled(getConfig().getBoolean("policy.high_impact.broadcasts", false));
        config.setEffectsEnabled(getConfig().getBoolean("policy.high_impact.effects", false));
        config.setAuditEnabled(getConfig().getBoolean("policy.audit.enabled", true));
        config.setAuditPath(getConfig().getString("policy.audit.path", "logs/zeus-punishment-audit.jsonl"));
        config.setPunishmentCooldownSeconds(getConfig().getInt("policy.cooldowns.punishment_seconds", 30));
        config.setBroadcastCooldownSeconds(getConfig().getInt("policy.cooldowns.broadcast_seconds", 30));
        config.setEffectCooldownSeconds(getConfig().getInt("policy.cooldowns.effect_seconds", 10));
        config.setStatusRefreshCooldownSeconds(getConfig().getInt("policy.cooldowns.status_refresh_seconds", 5));
        config.setGuiRefreshCooldownSeconds(getConfig().getInt("policy.cooldowns.gui_refresh_seconds", 5));
        config.setBanwaveEnabled(getConfig().getBoolean("banwave.enabled", false));

        // Load legacy action rules and policy profile overrides
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

        org.bukkit.configuration.ConfigurationSection profileSec = getConfig().getConfigurationSection("profiles");
        if (profileSec != null) {
            for (String profileId : profileSec.getKeys(false)) {
                config.setPolicyOverride(profileId, org.vennv.zeuspunishment.core.model.Severity.WARNING, profileSec.getString(profileId + ".warning_policy", "observe"));
                config.setPolicyOverride(profileId, org.vennv.zeuspunishment.core.model.Severity.KICK, profileSec.getString(profileId + ".kick_policy", "review"));
                config.setPolicyOverride(profileId, org.vennv.zeuspunishment.core.model.Severity.BAN, profileSec.getString(profileId + ".ban_policy", "review"));
            }
        }
    }

    public boolean reloadPlugin() {
        PunishmentConfig nextConfig;
        try {
            nextConfig = parsePluginConfigSnapshot();
            validatePluginConfigSnapshot(nextConfig);
        } catch (RuntimeException ex) {
            getLogger().warning("ZeusPunishment config invalid; keeping existing services active: " + ex.getMessage());
            return false;
        }
        java.util.List<String> oldModels = this.engine != null ? this.engine.getCachedModels() : new java.util.ArrayList<>();
        if (this.engine != null) {
            this.engine.stop();
        }
        this.config = nextConfig;
        this.apiClient = new ZeusApiClient(config.getEndpointUrl());
        org.vennv.zeuspunishment.core.audit.AuditSink auditSink = ZeusPunishmentEngine.createDefaultAuditSink(getDataFolder().toPath(), getLogger(), config.isAuditEnabled());
        org.vennv.zeuspunishment.core.cooldown.CooldownGate cooldownGate = new org.vennv.zeuspunishment.core.cooldown.CooldownGate();
        this.banwaveManager = new BanwaveManager(config, dispatcher, auditSink, cooldownGate);
        this.engine = new ZeusPunishmentEngine(config, dispatcher, apiClient, banwaveManager, auditSink, cooldownGate);
        this.engine.setCachedModels(oldModels);
        this.engine.start();
        return true;
    }

    public void reconnectStream() {
        if (this.engine != null) {
            this.engine.stop();
            this.engine.start();
        }
    }

    private PunishmentConfig parsePluginConfigSnapshot() {
        reloadConfig();
        PunishmentConfig previous = this.config;
        this.config = new PunishmentConfig();
        loadPluginConfig();
        PunishmentConfig parsed = this.config;
        this.config = previous;
        return parsed;
    }

    private void validatePluginConfigSnapshot(PunishmentConfig snapshot) {
        if (snapshot.getEndpointUrl() == null || snapshot.getEndpointUrl().isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        if (!snapshot.getEndpointUrl().startsWith("http://") && !snapshot.getEndpointUrl().startsWith("https://")) {
            throw new IllegalArgumentException("endpoint must be http or https");
        }
        if (!PunishmentConfig.allowedPolicyPresets().contains(snapshot.getPolicyPreset())) {
            throw new IllegalArgumentException("policy preset is invalid");
        }
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
