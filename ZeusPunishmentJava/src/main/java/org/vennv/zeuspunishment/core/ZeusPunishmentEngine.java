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
import java.util.concurrent.atomic.AtomicLong;

public class ZeusPunishmentEngine {
    private static final long PROBE_JOIN_MS = 3000L;

    private final PunishmentConfig config;
    private final PunishmentDispatcher dispatcher;
    private final ZeusApiClient apiClient;
    private final BanwaveManager banwaveManager;
    private final List<String> cachedModels = Collections.synchronizedList(new ArrayList<>());
    private final Object probeWaitMonitor = new Object();
    private final AtomicLong lifecycleGeneration = new AtomicLong(0L);

    private volatile boolean lifecycleRunning = false;
    private volatile boolean enforcementEnabled = false;
    private volatile Thread probeThread;

    public ZeusPunishmentEngine(PunishmentConfig config, PunishmentDispatcher dispatcher, ZeusApiClient apiClient, BanwaveManager banwaveManager) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.apiClient = apiClient;
        this.banwaveManager = banwaveManager;
    }

    /**
     * Starts health-gated background lifecycle work.
     */
    public void start() {
        stopProbeLoopOnly();
        lifecycleRunning = true;
        enforcementEnabled = false;
        long generation = lifecycleGeneration.incrementAndGet();
        Thread thread = new Thread(() -> runProbeLoop(generation), "Zeus-Health-Probe");
        probeThread = thread;
        thread.start();
    }

    /**
     * Gracefully stop all networking logic.
     */
    public void stop() {
        lifecycleRunning = false;
        enforcementEnabled = false;
        lifecycleGeneration.incrementAndGet();
        synchronized (probeWaitMonitor) {
            probeWaitMonitor.notifyAll();
        }
        Thread thread = probeThread;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(PROBE_JOIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!thread.isAlive() || thread == Thread.currentThread()) {
                probeThread = null;
            }
        }
        apiClient.stopStream();
    }

    private void runProbeLoop(long generation) {
        int delayMs = Math.max(1, config.getReconnectInitialMs());
        boolean loggedUnhealthy = false;
        while (isCurrentLifecycle(generation)) {
            boolean healthy = runBoundedStartupProbes(generation);
            if (healthy && isCurrentLifecycle(generation)) {
                enforcementEnabled = true;
                refreshConfiguredProfiles(generation);
                if (isCurrentLifecycle(generation)) {
                    apiClient.streamViolations(record -> {
                        if (enforcementEnabled && isCurrentLifecycle(generation)) {
                            processRecord(record);
                        }
                    });
                }
                return;
            }

            enforcementEnabled = false;
            if (!loggedUnhealthy && isCurrentLifecycle(generation)) {
                System.err.println("[ZeusPunishment] Public API compatibility probe failed; enforcement remains disabled until recovery.");
                loggedUnhealthy = true;
            }
            if (!waitForProbeRetry(generation, delayMs)) {
                return;
            }
            delayMs = Math.min(config.getReconnectMaxMs(), Math.max(config.getReconnectInitialMs(), delayMs * 2));
        }
    }

    private boolean runBoundedStartupProbes(long generation) {
        int attempts = Math.max(1, config.getHealthRetries());
        for (int i = 0; i < attempts && isCurrentLifecycle(generation); i++) {
            if (apiClient.probeCompatibility()) {
                return true;
            }
            if (i + 1 < attempts && !waitForProbeRetry(generation, config.getReconnectInitialMs())) {
                return false;
            }
        }
        return false;
    }

    private void refreshConfiguredProfiles(long generation) {
        if (!isCurrentLifecycle(generation)) return;
        List<String> fetched = apiClient.fetchActiveModels();
        if (fetched != null && isCurrentLifecycle(generation)) {
            cachedModels.clear();
            cachedModels.addAll(fetched);
        }
    }

    private boolean waitForProbeRetry(long generation, int delayMs) {
        long waitMs = Math.min(config.getReconnectMaxMs(), Math.max(1, delayMs));
        long deadline = System.currentTimeMillis() + waitMs;
        synchronized (probeWaitMonitor) {
            while (isCurrentLifecycle(generation)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return true;
                try {
                    probeWaitMonitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private void stopProbeLoopOnly() {
        lifecycleRunning = false;
        lifecycleGeneration.incrementAndGet();
        synchronized (probeWaitMonitor) {
            probeWaitMonitor.notifyAll();
        }
        Thread thread = probeThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(PROBE_JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        probeThread = null;
    }

    private boolean isCurrentLifecycle(long generation) {
        return lifecycleRunning && lifecycleGeneration.get() == generation;
    }

    private boolean processRecord(ViolationRecord record) {
        if (!enforcementEnabled) {
            return false;
        }

        // Find highest priority rule. Model rules take precedence over severity rules.
        ActionType finalAction = ActionType.NONE;
        long finalDuration = 86400000L; // Default 1 day ban

        // Check if there is a specific configured profile violation triggering an action based on severity
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

        // Get profile explanation if available for logging
        String explanation = "Policy evaluation";
        if (!record.getLogs().isEmpty() && record.getLogs().get(0).getExplanation() != null) {
            explanation = record.getLogs().get(0).getExplanation();
        }

        return executeAction(record, finalAction, finalDuration, explanation);
    }

    private boolean executeAction(ViolationRecord record, ActionType action, long duration, String explanation) {
        if (!enforcementEnabled) {
            return false;
        }
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
