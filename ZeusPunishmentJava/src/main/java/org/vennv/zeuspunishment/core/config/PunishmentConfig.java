package org.vennv.zeuspunishment.core.config;

import org.vennv.zeuspunishment.core.model.PolicyDecision;
import org.vennv.zeuspunishment.core.model.Severity;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PunishmentConfig {
    private static final Set<String> ALLOWED_POLICY_PRESETS = Collections.unmodifiableSet(new LinkedHashSet<>(Set.of("observe", "warn", "kick", "review", "enforce")));

    private String endpointUrl = "http://127.0.0.1:8080";
    private int pollIntervalSeconds = 0;
    private boolean devVerboseMode = false;
    private boolean devMode = false;
    private boolean effectsEnabled = false;
    private boolean dryRun = true;
    private boolean enforcementEnabled = false;
    private boolean immediateBanEnabled = false;
    private boolean broadcastsEnabled = false;
    private boolean manualBanwaveApprovalRequired = true;
    private String policyPreset = "observe";
    private String auditPath = "logs/zeus-punishment-audit.jsonl";
    private boolean auditEnabled = true;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 5000;
    private int reconnectInitialMs = 1000;
    private int reconnectMaxMs = 30000;
    private int healthRetries = 3;

    private final Map<String, Map<Severity, ActionType>> modelSeverityActions = new HashMap<>();
    private final Map<String, Map<Severity, String>> policyOverrides = new HashMap<>();

    private boolean banwaveEnabled = false;
    private int banwaveCountdownStartSeconds = 60;

    private int punishmentCooldownSeconds = 30;
    private int broadcastCooldownSeconds = 30;
    private int effectCooldownSeconds = 10;
    private int statusRefreshCooldownSeconds = 5;
    private int guiRefreshCooldownSeconds = 5;

    private String messageKick = "&cAction applied by Zeus policy.";
    private String messageBan = "&cAction applied by Zeus policy.";
    private String broadcastBan = "&e[Zeus] &7A policy action was applied for %player%.";
    private String banwaveSummary = "&e[Zeus] &7Review queue completed with &c%count% &7entries.";

    public static Set<String> allowedPolicyPresets() { return ALLOWED_POLICY_PRESETS; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = Math.max(0, pollIntervalSeconds); }
    public boolean isDevVerboseMode() { return devVerboseMode; }
    public void setDevVerboseMode(boolean devVerboseMode) { this.devVerboseMode = devVerboseMode; }
    public boolean isDevMode() { return devMode; }
    public void setDevMode(boolean devMode) { this.devMode = devMode; }
    public boolean isEffectsEnabled() { return effectsEnabled; }
    public void setEffectsEnabled(boolean effectsEnabled) { this.effectsEnabled = effectsEnabled; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public boolean isEnforcementEnabled() { return enforcementEnabled; }
    public void setEnforcementEnabled(boolean enforcementEnabled) { this.enforcementEnabled = enforcementEnabled; }
    public boolean isImmediateBanEnabled() { return immediateBanEnabled; }
    public void setImmediateBanEnabled(boolean immediateBanEnabled) { this.immediateBanEnabled = immediateBanEnabled; }
    public boolean isBroadcastsEnabled() { return broadcastsEnabled; }
    public void setBroadcastsEnabled(boolean broadcastsEnabled) { this.broadcastsEnabled = broadcastsEnabled; }
    public boolean isManualBanwaveApprovalRequired() { return manualBanwaveApprovalRequired; }
    public void setManualBanwaveApprovalRequired(boolean manualBanwaveApprovalRequired) { this.manualBanwaveApprovalRequired = manualBanwaveApprovalRequired; }
    public String getPolicyPreset() { return policyPreset; }
    public boolean setPolicyPreset(String policyPreset) {
        String normalized = normalizePreset(policyPreset);
        if (normalized == null) { this.policyPreset = "observe"; this.dryRun = true; this.enforcementEnabled = false; return false; }
        this.policyPreset = normalized; return true;
    }
    public String getAuditPath() { return auditPath; }
    public void setAuditPath(String auditPath) { if (auditPath != null && !auditPath.isBlank()) this.auditPath = auditPath; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(boolean auditEnabled) { this.auditEnabled = auditEnabled; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = Math.max(1, connectTimeoutMs); }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = Math.max(1, readTimeoutMs); }
    public int getReconnectInitialMs() { return reconnectInitialMs; }
    public void setReconnectInitialMs(int reconnectInitialMs) { this.reconnectInitialMs = Math.max(1, reconnectInitialMs); }
    public int getReconnectMaxMs() { return reconnectMaxMs; }
    public void setReconnectMaxMs(int reconnectMaxMs) { this.reconnectMaxMs = Math.max(1, reconnectMaxMs); }
    public int getHealthRetries() { return healthRetries; }
    public void setHealthRetries(int healthRetries) { this.healthRetries = Math.max(1, healthRetries); }

    public ActionType getActionForModel(String modelId, Severity severity) {
        Map<Severity, ActionType> actions = modelSeverityActions.get(modelId);
        return actions != null && actions.containsKey(severity) ? actions.get(severity) : ActionType.NONE;
    }
    public void setActionForModel(String modelId, Severity severity, ActionType action) { modelSeverityActions.computeIfAbsent(modelId, k -> new HashMap<>()).put(severity, action == null ? ActionType.NONE : action); }

    public boolean setPolicyOverride(String profile, Severity severity, String preset) {
        String normalized = normalizePreset(preset);
        if (normalized == null) return false;
        policyOverrides.computeIfAbsent(profile, k -> new HashMap<>()).put(severity, normalized);
        return true;
    }

    public PolicyDecision resolvePolicyDecision(String profile, Severity severity, String stableKey) {
        String tier = policyOverrides.getOrDefault(profile, Collections.emptyMap()).getOrDefault(severity, policyPreset);
        PolicyDecision.PolicyAction action = actionForPreset(tier, severity);
        boolean highImpact = switch (action) {
            case BAN -> immediateBanEnabled;
            case BANWAVE -> banwaveEnabled;
            case EFFECT -> effectsEnabled;
            default -> true;
        };
        boolean effectiveDryRun = dryRun || !enforcementEnabled || !highImpact;
        return new PolicyDecision(tier, action, effectiveDryRun, highImpact, stableKey, "policy tier " + tier);
    }

    private static String normalizePreset(String preset) {
        if (preset == null) return null;
        String normalized = preset.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_POLICY_PRESETS.contains(normalized) ? normalized : null;
    }

    private static PolicyDecision.PolicyAction actionForPreset(String preset, Severity severity) {
        return switch (preset) {
            case "warn" -> PolicyDecision.PolicyAction.WARN;
            case "kick" -> PolicyDecision.PolicyAction.KICK;
            case "review" -> PolicyDecision.PolicyAction.BANWAVE;
            case "enforce" -> severity == Severity.BAN ? PolicyDecision.PolicyAction.BAN : PolicyDecision.PolicyAction.KICK;
            default -> PolicyDecision.PolicyAction.LOG;
        };
    }

    public boolean isBanwaveEnabled() { return banwaveEnabled; }
    public void setBanwaveEnabled(boolean banwaveEnabled) { this.banwaveEnabled = banwaveEnabled; }
    public int getBanwaveCountdownStartSeconds() { return banwaveCountdownStartSeconds; }
    public void setBanwaveCountdownStartSeconds(int seconds) { this.banwaveCountdownStartSeconds = Math.max(0, seconds); }
    public int getPunishmentCooldownSeconds() { return punishmentCooldownSeconds; }
    public void setPunishmentCooldownSeconds(int seconds) { this.punishmentCooldownSeconds = Math.max(0, seconds); }
    public int getBroadcastCooldownSeconds() { return broadcastCooldownSeconds; }
    public void setBroadcastCooldownSeconds(int seconds) { this.broadcastCooldownSeconds = Math.max(0, seconds); }
    public int getEffectCooldownSeconds() { return effectCooldownSeconds; }
    public void setEffectCooldownSeconds(int seconds) { this.effectCooldownSeconds = Math.max(0, seconds); }
    public int getStatusRefreshCooldownSeconds() { return statusRefreshCooldownSeconds; }
    public long statusRefreshWindowMillis() { return statusRefreshCooldownSeconds * 1000L; }
    public void setStatusRefreshCooldownSeconds(int seconds) { this.statusRefreshCooldownSeconds = Math.max(0, seconds); }
    public int getGuiRefreshCooldownSeconds() { return guiRefreshCooldownSeconds; }
    public long guiRefreshWindowMillis() { return guiRefreshCooldownSeconds * 1000L; }
    public void setGuiRefreshCooldownSeconds(int seconds) { this.guiRefreshCooldownSeconds = Math.max(0, seconds); }
    public String getMessageKick() { return messageKick; }
    public void setMessageKick(String messageKick) { this.messageKick = messageKick; }
    public String getMessageBan() { return messageBan; }
    public void setMessageBan(String messageBan) { this.messageBan = messageBan; }
    public String getBroadcastBan() { return broadcastBan; }
    public void setBroadcastBan(String broadcastBan) { this.broadcastBan = broadcastBan; }
    public String getBanwaveSummary() { return banwaveSummary; }
    public void setBanwaveSummary(String banwaveSummary) { this.banwaveSummary = banwaveSummary; }
}
