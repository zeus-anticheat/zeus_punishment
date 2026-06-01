package org.vennv.zeuspunishment.core.config;

import org.vennv.zeuspunishment.core.model.Severity;

import java.util.HashMap;
import java.util.Map;

public class PunishmentConfig {
    private String endpointUrl = "http://127.0.0.1:8080";
    private int pollIntervalSeconds = 0;
    private boolean devVerboseMode = false;
    private boolean devMode = false;
    private boolean effectsEnabled = true;

    // Model severity rules
    private final Map<String, Map<Severity, ActionType>> modelSeverityActions = new HashMap<>();

    // Banwave setup
    private boolean banwaveEnabled = false;
    private int banwaveCountdownStartSeconds = 60; // Start countdown when Banwave is triggered

    // Messages
    private String messageKick = "&cYou have been kicked by Zeus Anti-Cheat!";
    private String messageBan = "&cYou have been banned by Zeus Anti-Cheat!";
    private String broadcastBan = "&e[Zeus] &c%player% &7was caught cheating by Zeus and has been removed.";
    private String banwaveSummary = "&e[Zeus] &7Banwave completed! Removed &c%count% &7cheaters.";

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }

    public boolean isDevVerboseMode() { return devVerboseMode; }
    public void setDevVerboseMode(boolean devVerboseMode) { this.devVerboseMode = devVerboseMode; }

    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }

    public boolean isEffectsEnabled() { return effectsEnabled; }
    public void setEffectsEnabled(boolean effectsEnabled) { this.effectsEnabled = effectsEnabled; }

    public ActionType getActionForModel(String modelId, Severity severity) {
        Map<Severity, ActionType> actions = modelSeverityActions.get(modelId);
        if (actions != null && actions.containsKey(severity)) {
            return actions.get(severity);
        }
        return ActionType.NONE; // Fallback if no specific config
    }

    public void setActionForModel(String modelId, Severity severity, ActionType action) {
        modelSeverityActions.computeIfAbsent(modelId, k -> new HashMap<>()).put(severity, action);
    }

    public boolean isBanwaveEnabled() { return banwaveEnabled; }
    public void setBanwaveEnabled(boolean banwaveEnabled) { this.banwaveEnabled = banwaveEnabled; }

    public int getBanwaveCountdownStartSeconds() { return banwaveCountdownStartSeconds; }
    public void setBanwaveCountdownStartSeconds(int banwaveCountdownStartSeconds) { this.banwaveCountdownStartSeconds = banwaveCountdownStartSeconds; }

    public String getMessageKick() { return messageKick; }
    public void setMessageKick(String messageKick) { this.messageKick = messageKick; }

    public String getMessageBan() { return messageBan; }
    public void setMessageBan(String messageBan) { this.messageBan = messageBan; }

    public String getBroadcastBan() { return broadcastBan; }
    public void setBroadcastBan(String broadcastBan) { this.broadcastBan = broadcastBan; }

    public String getBanwaveSummary() { return banwaveSummary; }
    public void setBanwaveSummary(String banwaveSummary) { this.banwaveSummary = banwaveSummary; }
}
