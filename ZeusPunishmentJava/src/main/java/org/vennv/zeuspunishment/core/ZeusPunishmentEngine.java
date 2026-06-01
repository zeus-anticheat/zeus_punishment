package org.vennv.zeuspunishment.core;

import org.vennv.zeuspunishment.core.audit.AuditEvent;
import org.vennv.zeuspunishment.core.audit.AuditSink;
import org.vennv.zeuspunishment.core.audit.CompositeAuditSink;
import org.vennv.zeuspunishment.core.audit.FileAuditSink;
import org.vennv.zeuspunishment.core.audit.NoopAuditSink;
import org.vennv.zeuspunishment.core.config.ActionType;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.cooldown.CooldownGate;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.EngineStatusSnapshot;
import org.vennv.zeuspunishment.core.model.PolicyDecision;
import org.vennv.zeuspunishment.core.model.ViolationKey;
import org.vennv.zeuspunishment.core.model.ViolationLog;
import org.vennv.zeuspunishment.core.model.ViolationRecord;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class ZeusPunishmentEngine {
    private static final long PROBE_JOIN_MS = 3000L;
    private static final long DEFAULT_DEDUP_WINDOW_MS = 300000L;

    private final PunishmentConfig config;
    private final PunishmentDispatcher dispatcher;
    private final ZeusApiClient apiClient;
    private final BanwaveManager banwaveManager;
    private final AuditSink auditSink;
    private final CooldownGate cooldownGate;
    private final List<String> cachedModels = Collections.synchronizedList(new ArrayList<>());
    private final Map<ViolationKey, DedupEntry> processedViolations = new ConcurrentHashMap<>();
    private final Map<ViolationKey, DedupEntry> pendingViolations = new ConcurrentHashMap<>();
    private final Object probeWaitMonitor = new Object();
    private final AtomicLong lifecycleGeneration = new AtomicLong(0L);

    private volatile boolean lifecycleRunning = false;
    private volatile boolean enforcementEnabled = false;
    private volatile Thread probeThread;
    private final List<String> recentOutcomes = Collections.synchronizedList(new ArrayList<>());

    public ZeusPunishmentEngine(PunishmentConfig config, PunishmentDispatcher dispatcher, ZeusApiClient apiClient, BanwaveManager banwaveManager) {
        this(config, dispatcher, apiClient, banwaveManager, NoopAuditSink.INSTANCE, new CooldownGate());
    }

    public ZeusPunishmentEngine(PunishmentConfig config, PunishmentDispatcher dispatcher, ZeusApiClient apiClient, BanwaveManager banwaveManager, AuditSink auditSink, CooldownGate cooldownGate) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.apiClient = apiClient;
        this.banwaveManager = banwaveManager;
        this.auditSink = auditSink == null ? NoopAuditSink.INSTANCE : auditSink;
        this.cooldownGate = cooldownGate == null ? new CooldownGate() : cooldownGate;
    }

    public static AuditSink createDefaultAuditSink(Path dataFolder, Logger logger, boolean enabled) {
        if (!enabled) return NoopAuditSink.INSTANCE;
        return new CompositeAuditSink(FileAuditSink.forDataFolder(dataFolder), AuditSink.loggerSummary(logger));
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

    public boolean processRecord(ViolationRecord record) {
        cleanupDedupCaches();
        ViolationKey key = ViolationKey.from(record);
        String profile = firstProfile(record);
        String stableKey = record.getUid() + ":" + profile + ":" + record.getSeverity().name();
        ActionType legacyAction = selectLegacyAction(record);
        boolean legacyRule = legacyAction != ActionType.NONE;
        if (legacyRule) {
            if (processedViolations.containsKey(key) || pendingViolations.containsKey(key)) {
                return false;
            }
            pendingViolations.put(key, DedupEntry.now());
        }

        PolicyDecision decision = legacyRule
                ? new PolicyDecision("legacy", policyActionForLegacy(legacyAction), false, true, stableKey, "legacy action rule")
                : config.resolvePolicyDecision(profile, record.getSeverity(), stableKey);
        audit(decision, record, "decision", "selected", "none", "none", "none", null, false);

        DispatcherOutcome outcome = evaluatePolicy(record, decision);
        rememberOutcome(decision.action().name().toLowerCase() + " " + outcome.status().name().toLowerCase());
        boolean acknowledged = acknowledgeAfterSafeAcceptance(record, outcome);
        auditAcknowledgement(decision, record, outcome, acknowledged);
        if (legacyRule) {
            applyDedupOutcome(key, outcome, acknowledged);
        }
        return outcome.isSuccessfulAcceptance() || (!legacyRule && outcome.status() == DispatcherOutcome.Status.IGNORED);
    }

    private DispatcherOutcome evaluatePolicy(ViolationRecord record, PolicyDecision decision) {
        if (decision.action() == PolicyDecision.PolicyAction.LOG) {
            return DispatcherOutcome.ignored("policy log only");
        }
        if (decision.dryRun()) {
            audit(decision, record, "dry-run", "would-act", "suppressed", "none", "not-acknowledged", null, false);
            return DispatcherOutcome.ignored("policy dry-run");
        }

        CooldownGate.Decision cooldown = cooldownGate.check(record.getUid(), decision.stableKey(), CooldownGate.Category.PUNISHMENT, Duration.ofSeconds(config.getPunishmentCooldownSeconds()));
        if (!cooldown.allowed()) {
            audit(decision, record, "cooldown", "suppressed", "suppressed", "none", "not-acknowledged", cooldown, false);
            return DispatcherOutcome.ignored("cooldown suppressed");
        }

        return executePolicyAction(record, decision, cooldown);
    }

    private DispatcherOutcome executePolicyAction(ViolationRecord record, PolicyDecision decision, CooldownGate.Decision cooldown) {
        DispatcherOutcome outcome;
        String queueResult = "none";
        switch (decision.action()) {
            case WARN -> outcome = dispatcher.logVerbose("Policy warning recorded for " + record.getUsername());
            case KICK -> {
                outcome = dispatcher.kickPlayer(record, config.getMessageKick());
                if (outcome.status() == DispatcherOutcome.Status.EXECUTED) {
                    maybeEffect(record, decision);
                    maybeBroadcast(record, decision);
                }
            }
            case BANWAVE -> {
                outcome = banwaveManager.queueBan(record);
                queueResult = outcome.status() == DispatcherOutcome.Status.QUEUED ? "queued" : outcome.status().name().toLowerCase();
            }
            case BAN -> {
                if (config.isBanwaveEnabled()) {
                    outcome = banwaveManager.queueBan(record);
                    queueResult = outcome.status() == DispatcherOutcome.Status.QUEUED ? "queued" : outcome.status().name().toLowerCase();
                } else {
                    outcome = dispatcher.banPlayer(record, config.getMessageBan(), 86400000L);
                    if (outcome.status() == DispatcherOutcome.Status.EXECUTED) {
                        maybeEffect(record, decision);
                        maybeBroadcast(record, decision);
                    }
                }
            }
            case EFFECT -> outcome = maybeEffect(record, decision);
            case LOG -> outcome = DispatcherOutcome.ignored("policy log only");
            default -> outcome = DispatcherOutcome.ignored("no configured action");
        }
        audit(decision, record, "action", outcome.isSuccessfulAcceptance() ? "applied" : "failed", outcome.isSuccessfulAcceptance() ? "dispatched" : outcome.status().name().toLowerCase(), queueResult, outcome.isSuccessfulAcceptance() ? "acknowledged" : "pending", cooldown, outcome.status() == DispatcherOutcome.Status.RETRYABLE_FAILURE);
        return outcome;
    }

    private DispatcherOutcome maybeBroadcast(ViolationRecord record, PolicyDecision decision) {
        if (!config.isBroadcastsEnabled()) {
            audit(decision, record, "control", "suppressed", "suppressed", "none", "none", null, false);
            return DispatcherOutcome.ignored("broadcast disabled");
        }
        CooldownGate.Decision cooldown = cooldownGate.check(record.getUid(), decision.stableKey(), CooldownGate.Category.BROADCAST, Duration.ofSeconds(config.getBroadcastCooldownSeconds()));
        DispatcherOutcome outcome = cooldown.allowed()
                ? dispatcher.broadcast(config.getBroadcastBan().replace("%player%", record.getUsername()))
                : DispatcherOutcome.ignored("broadcast cooldown suppressed");
        audit(decision, record, "broadcast", cooldown.allowed() ? "applied" : "suppressed", outcome.status().name().toLowerCase(), "none", "none", cooldown, outcome.status() == DispatcherOutcome.Status.RETRYABLE_FAILURE);
        return outcome;
    }

    private DispatcherOutcome maybeEffect(ViolationRecord record, PolicyDecision decision) {
        if (!config.isEffectsEnabled()) {
            audit(decision, record, "control", "suppressed", "suppressed", "none", "none", null, false);
            return DispatcherOutcome.ignored("effect disabled");
        }
        CooldownGate.Decision cooldown = cooldownGate.check(record.getUid(), decision.stableKey(), CooldownGate.Category.EFFECT, Duration.ofSeconds(config.getEffectCooldownSeconds()));
        DispatcherOutcome outcome = cooldown.allowed()
                ? dispatcher.playEffect(record.getUid())
                : DispatcherOutcome.ignored("effect cooldown suppressed");
        audit(decision, record, "effect", cooldown.allowed() ? "applied" : "suppressed", outcome.status().name().toLowerCase(), "none", "none", cooldown, outcome.status() == DispatcherOutcome.Status.RETRYABLE_FAILURE);
        return outcome;
    }

    private void auditAcknowledgement(PolicyDecision decision, ViolationRecord record, DispatcherOutcome outcome, boolean acknowledged) {
        if (!outcome.isSuccessfulAcceptance()) {
            return;
        }
        audit(decision, record, "ack", acknowledged ? "acknowledged" : "failed", outcome.status().name().toLowerCase(), "none", acknowledged ? "acknowledged" : "failed", null, !acknowledged);
    }

    private void audit(PolicyDecision decision, ViolationRecord record, String eventType, String outcome, String dispatcherOutcome, String queueResult, String ackResult, CooldownGate.Decision cooldown, boolean retryable) {
        auditSink.record(AuditEvent.builder(eventType)
                .stableId(decision.stableKey())
                .playerId(record.getUid())
                .policyTier(decision.policyTier())
                .policyAction(decision.action().name())
                .dispatcherOutcome(dispatcherOutcome)
                .queueResult(queueResult)
                .ackResult(ackResult)
                .cooldownAllowed(cooldown == null ? null : cooldown.allowed())
                .cooldownCategory(cooldown == null ? "none" : cooldown.category().name())
                .cooldownSuppressionCount(cooldown == null ? 0 : cooldown.suppressionCount())
                .dryRun(decision.dryRun())
                .highImpactAllowed(decision.highImpactAllowed())
                .outcome(outcome)
                .retryable(retryable)
                .build());
    }

    private boolean acknowledgeAfterSafeAcceptance(ViolationRecord record, DispatcherOutcome outcome) {
        if (!outcome.isSuccessfulAcceptance()) {
            return false;
        }
        if (record.getUid() == null || record.getUid().trim().isEmpty()) {
            return false;
        }
        return apiClient.acknowledgeViolations(Collections.singletonList(record.getUid()));
    }

    private void applyDedupOutcome(ViolationKey key, DispatcherOutcome outcome, boolean acknowledged) {
        if (outcome.status() == DispatcherOutcome.Status.RETRYABLE_FAILURE || (outcome.isSuccessfulAcceptance() && !acknowledged)) {
            pendingViolations.put(key, DedupEntry.now());
            return;
        }
        pendingViolations.remove(key);
        if (outcome.isSuccessfulAcceptance()) {
            processedViolations.put(key, DedupEntry.now());
        }
    }

    private void cleanupDedupCaches() {
        cleanup(processedViolations);
        cleanup(pendingViolations);
    }

    private void cleanup(Map<ViolationKey, DedupEntry> cache) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<ViolationKey, DedupEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().createdAtMs > DEFAULT_DEDUP_WINDOW_MS) {
                iterator.remove();
            }
        }
    }

    private ActionType selectLegacyAction(ViolationRecord record) {
        for (ViolationLog log : record.getLogs()) {
            ActionType action = config.getActionForModel(log.getModelId(), record.getSeverity());
            if (action != ActionType.NONE) {
                return action;
            }
        }
        return ActionType.NONE;
    }

    private static PolicyDecision.PolicyAction policyActionForLegacy(ActionType action) {
        return switch (action) {
            case KICK -> PolicyDecision.PolicyAction.KICK;
            case BAN -> PolicyDecision.PolicyAction.BAN;
            default -> PolicyDecision.PolicyAction.LOG;
        };
    }

    private static String firstProfile(ViolationRecord record) {
        for (ViolationLog log : record.getLogs()) {
            if (log.getModelId() != null && !log.getModelId().isBlank()) return log.getModelId();
        }
        return "default";
    }

    public List<String> getCachedModels() {
        return new ArrayList<>(cachedModels);
    }

    public void setCachedModels(List<String> models) {
        cachedModels.clear();
        if (models != null) cachedModels.addAll(models);
    }

    public EngineStatusSnapshot getStatusSnapshot() {
        return new EngineStatusSnapshot(
                lifecycleRunning,
                enforcementEnabled,
                config.isDryRun(),
                config.getPolicyPreset(),
                config.isImmediateBanEnabled() || config.isBanwaveEnabled() || config.isBroadcastsEnabled() || config.isEffectsEnabled(),
                apiClient.getStatusSnapshot(),
                banwaveManager.getQueueState(),
                getRecentOutcomesCopy());
    }

    private void rememberOutcome(String summary) {
        synchronized (recentOutcomes) {
            recentOutcomes.add(summary == null ? "outcome recorded" : summary);
            while (recentOutcomes.size() > 5) recentOutcomes.remove(0);
        }
    }

    private List<String> getRecentOutcomesCopy() {
        synchronized (recentOutcomes) {
            return new ArrayList<>(recentOutcomes);
        }
    }

    private static final class DedupEntry {
        private final long createdAtMs;

        private DedupEntry(long createdAtMs) {
            this.createdAtMs = createdAtMs;
        }

        private static DedupEntry now() {
            return new DedupEntry(System.currentTimeMillis());
        }
    }
}
