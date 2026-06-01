package org.vennv.zeuspunishment.core;

import org.vennv.zeuspunishment.core.config.ActionType;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.ViolationLog;
import org.vennv.zeuspunishment.core.model.ViolationRecord;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ZeusPunishmentEngine {
    private final PunishmentConfig config;
    private final PunishmentDispatcher dispatcher;
    private final ZeusApiClient apiClient;
    private final BanwaveManager banwaveManager;
    private final List<String> cachedModels = Collections.synchronizedList(new ArrayList<>());

    public ZeusPunishmentEngine(PunishmentConfig config, PunishmentDispatcher dispatcher, ZeusApiClient apiClient, BanwaveManager banwaveManager) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.apiClient = apiClient;
        this.banwaveManager = banwaveManager;
    }

    /**
     * Starts the SSE connection and background model fetching thread.
     */
    public void start() {
        // Cache available models from backend async to prevent GUI lag
        new Thread(() -> {
            List<String> fetched = apiClient.fetchActiveModels();
            if (fetched != null) {
                cachedModels.clear();
                cachedModels.addAll(fetched);
            }
        }, "Zeus-Model-Fetcher").start();

        // Start 0-latency stream
        apiClient.streamViolations(record -> {
            boolean processed = processRecord(record);
            if (processed) {
                apiClient.clearViolations(Collections.singletonList(record.getUid()));
            }
        });
    }

    /**
     * Gracefully stop all networking logic
     */
    public void stop() {
        apiClient.stopStream();
    }

    private boolean processRecord(ViolationRecord record) {
        // Find highest priority rule. Model rules take precedence over severity rules.
        ActionType finalAction = ActionType.NONE;
        long finalDuration = 86400000L; // Default 1 day ban

        // Check if there is a specific model violation triggering an action based on severity
        for (ViolationLog log : record.getLogs()) {
            ActionType action = config.getActionForModel(log.getModelId(), record.getSeverity());
            if (action != ActionType.NONE) {
                finalAction = action;
                break;
            }
        }

        if (finalAction == ActionType.NONE) {
            return false; // Not processed, no rule matched
        }

        // Get model explanation if available for logging
        String explanation = "Heuristic detection";
        if (!record.getLogs().isEmpty() && record.getLogs().get(0).getExplanation() != null) {
            explanation = record.getLogs().get(0).getExplanation();
        }

        // Execute 
        return executeAction(record, finalAction, finalDuration, explanation);
    }

    private boolean executeAction(ViolationRecord record, ActionType action, long duration, String explanation) {
        String logMsg = String.format("Player %s punished. Action: %s. Points: %.2f. Reason: %s",
                record.getUsername(), action.name(), record.getTotalPoints(), explanation);

        if (action == ActionType.BAN) {
            if (config.isBanwaveEnabled()) {
                if (!banwaveManager.isQueued(record.getUid())) {
                    banwaveManager.queueBan(record);
                    if (config.isDevVerboseMode()) dispatcher.logVerbose("Queued for Banwave: " + record.getUsername() + " | Explanation: " + explanation);
                }
                return true; // Marked as processed since it's queued
            } else {
                dispatcher.banPlayer(record, "Zeus Anti-Cheat Violation", duration);
                dispatcher.playEffect(record.getUid());
                dispatcher.broadcast(config.getBroadcastBan().replace("%player%", record.getUsername()));
                if (config.isDevVerboseMode()) dispatcher.logVerbose(logMsg);
            }
        } else if (action == ActionType.KICK) {
            dispatcher.kickPlayer(record, "Zeus Anti-Cheat Violation");
            dispatcher.playEffect(record.getUid());
            dispatcher.broadcast(config.getBroadcastBan().replace("%player%", record.getUsername()));
            if (config.isDevVerboseMode()) dispatcher.logVerbose(logMsg);
        } else if (action == ActionType.SETBACK) {
            dispatcher.setbackPlayer(record);
            if (config.isDevVerboseMode()) dispatcher.logVerbose(logMsg);
            // No broadcast, no effect, no file logging for SETBACK
            return true;
        } else if (action == ActionType.MITIGATE) {
            dispatcher.mitigatePlayer(record);
            if (config.isDevVerboseMode()) dispatcher.logVerbose(logMsg);
            return true;
        }

        return true;
    }

    public List<String> getCachedModels() {
        return new ArrayList<>(cachedModels);
    }

    public void setCachedModels(List<String> models) {
        cachedModels.clear();
        if (models != null) cachedModels.addAll(models);
    }
}
