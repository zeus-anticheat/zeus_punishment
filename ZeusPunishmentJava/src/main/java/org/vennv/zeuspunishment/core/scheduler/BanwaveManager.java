package org.vennv.zeuspunishment.core.scheduler;

import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.audit.AuditEvent;
import org.vennv.zeuspunishment.core.audit.AuditSink;
import org.vennv.zeuspunishment.core.audit.NoopAuditSink;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.cooldown.CooldownGate;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.ViolationKey;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BanwaveManager {
    private final List<ViolationRecord> queuedBans = new ArrayList<>();
    private final PunishmentConfig config;
    private final PunishmentDispatcher dispatcher;
    private final AuditSink auditSink;
    private final CooldownGate cooldownGate;

    private boolean isCountingDown = false;
    private boolean paused = false;
    private int secondsRemaining = 0;
    private int duplicateSuppressions = 0;

    public BanwaveManager(PunishmentConfig config, PunishmentDispatcher dispatcher) {
        this(config, dispatcher, NoopAuditSink.INSTANCE, new CooldownGate());
    }

    public BanwaveManager(PunishmentConfig config, PunishmentDispatcher dispatcher, AuditSink auditSink, CooldownGate cooldownGate) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.auditSink = auditSink == null ? NoopAuditSink.INSTANCE : auditSink;
        this.cooldownGate = cooldownGate == null ? new CooldownGate() : cooldownGate;
    }

    public synchronized DispatcherOutcome queueBan(ViolationRecord record) {
        if (record == null || isQueued(ViolationKey.from(record))) {
            duplicateSuppressions++;
            audit("banwave-queue", record, "suppressed", "duplicate");
            return DispatcherOutcome.ignored("queue duplicate suppressed");
        }
        queuedBans.add(record);
        audit("banwave-queue", record, "queued", "queued");
        return DispatcherOutcome.queued("queued for review");
    }

    public synchronized boolean isQueued(String uid) {
        for (ViolationRecord record : queuedBans) {
            if (record.getUid().equals(uid)) return true;
        }
        return false;
    }

    public synchronized boolean isQueued(ViolationKey key) {
        for (ViolationRecord record : queuedBans) {
            if (ViolationKey.from(record).equals(key)) return true;
        }
        return false;
    }

    public synchronized void startCountdown() {
        resume();
    }

    public synchronized ControlResult resume() {
        paused = false;
        if (!queuedBans.isEmpty() && !isCountingDown) {
            isCountingDown = true;
            secondsRemaining = Math.max(0, config.getBanwaveCountdownStartSeconds());
        }
        auditControl("banwave-resume", "resumed");
        return new ControlResult(true, "banwave review resumed");
    }

    public synchronized ControlResult pause() {
        paused = true;
        isCountingDown = false;
        auditControl("banwave-pause", "paused");
        return new ControlResult(true, "banwave review paused");
    }

    public synchronized ControlResult clearQueue() {
        int count = queuedBans.size();
        queuedBans.clear();
        isCountingDown = false;
        secondsRemaining = 0;
        auditControl("banwave-clear", "cleared " + count);
        return new ControlResult(true, "cleared " + count + " queued entr" + (count == 1 ? "y" : "ies"));
    }

    public synchronized ControlResult cancel(String key) {
        Iterator<ViolationRecord> iterator = queuedBans.iterator();
        while (iterator.hasNext()) {
            ViolationRecord record = iterator.next();
            if (ViolationKey.from(record).asString().equals(key)) {
                iterator.remove();
                if (queuedBans.isEmpty()) {
                    isCountingDown = false;
                    secondsRemaining = 0;
                }
                audit("banwave-cancel", record, "cancelled", "cancelled");
                return new ControlResult(true, "cancelled queued entry " + key);
            }
        }
        return new ControlResult(false, "queued entry not found");
    }

    public synchronized QueueEntry details(String key) {
        for (ViolationRecord record : queuedBans) {
            ViolationKey violationKey = ViolationKey.from(record);
            if (violationKey.asString().equals(key)) return QueueEntry.from(record, violationKey);
        }
        return null;
    }

    public synchronized List<QueueEntry> list() {
        List<QueueEntry> entries = new ArrayList<>();
        for (ViolationRecord record : queuedBans) entries.add(QueueEntry.from(record, ViolationKey.from(record)));
        return entries;
    }

    public synchronized ControlResult executeAll() {
        if (queuedBans.isEmpty()) return new ControlResult(false, "no queued entries to execute");
        executeBanwave();
        return new ControlResult(true, "banwave execution requested");
    }

    /** Called every second by the platform-specific scheduler (Bukkit/Fabric). */
    public synchronized void tickSecond() {
        if (paused || !isCountingDown) return;

        if (secondsRemaining > 0) {
            if (secondsRemaining == 60 || secondsRemaining == 30 || secondsRemaining <= 5) {
                broadcastCooldown("&e[Zeus] &cBanwave review executes in " + secondsRemaining + "s.");
            }
            secondsRemaining--;
        } else {
            executeBanwave();
        }
    }

    private void executeBanwave() {
        int count = queuedBans.size();
        List<ViolationRecord> records = new ArrayList<>(queuedBans);
        for (ViolationRecord record : records) {
            CooldownGate.Decision punishment = cooldownGate.check(record.getUid(), ViolationKey.from(record).asString(), CooldownGate.Category.PUNISHMENT, Duration.ofSeconds(config.getPunishmentCooldownSeconds()));
            if (punishment.allowed()) {
                dispatcher.banPlayer(record, "Zeus Banwave", 0L);
                effectCooldown(record);
                audit("banwave-execute", record, "executed", "dispatched");
            } else {
                audit("banwave-execute", record, "suppressed", "cooldown");
            }
        }
        broadcastCooldown(config.getBanwaveSummary().replace("%count%", String.valueOf(count)));
        clearQueue();
    }

    private void broadcastCooldown(String message) {
        CooldownGate.Decision cooldown = cooldownGate.check("banwave", message, CooldownGate.Category.BROADCAST, Duration.ofSeconds(config.getBroadcastCooldownSeconds()));
        if (cooldown.allowed()) dispatcher.broadcast(message);
        else auditControl("banwave-broadcast", "suppressed");
    }

    private void effectCooldown(ViolationRecord record) {
        CooldownGate.Decision cooldown = cooldownGate.check(record.getUid(), ViolationKey.from(record).asString(), CooldownGate.Category.EFFECT, Duration.ofSeconds(config.getEffectCooldownSeconds()));
        if (cooldown.allowed()) dispatcher.playEffect(record.getUid());
        else audit("banwave-effect", record, "suppressed", "cooldown");
    }

    public synchronized List<ViolationRecord> getQueuedBans() { return new ArrayList<>(queuedBans); }

    public synchronized QueueState getQueueState() {
        List<ViolationRecord> records = new ArrayList<>(queuedBans);
        List<String> keys = new ArrayList<>();
        List<QueueEntry> entries = new ArrayList<>();
        for (ViolationRecord record : records) {
            ViolationKey key = ViolationKey.from(record);
            keys.add(key.asString());
            entries.add(QueueEntry.from(record, key));
        }
        return new QueueState(queuedBans.size(), isCountingDown, paused, secondsRemaining, duplicateSuppressions, records, keys, entries);
    }

    private void audit(String event, ViolationRecord record, String outcome, String queueResult) {
        auditSink.record(AuditEvent.builder(event)
                .stableId(record == null ? "unknown" : ViolationKey.from(record).asString())
                .playerId(record == null ? "unknown" : record.getUid())
                .policyTier("banwave")
                .policyAction("BAN")
                .queueResult(queueResult)
                .outcome(outcome)
                .build());
    }

    private void auditControl(String event, String outcome) {
        auditSink.record(AuditEvent.builder(event).policyTier("banwave").policyAction("CONTROL").outcome(outcome).build());
    }

    public record ControlResult(boolean success, String message) { }
    public record QueueEntry(String key, String uid, String username, String severity) {
        static QueueEntry from(ViolationRecord record, ViolationKey key) {
            return new QueueEntry(key.asString(), record.getUid(), record.getUsername(), record.getSeverity().name().toLowerCase());
        }
    }

    public static final class QueueState {
        private final int queuedCount;
        private final boolean countingDown;
        private final boolean paused;
        private final int secondsRemaining;
        private final int duplicateSuppressions;
        private final List<ViolationRecord> queuedRecords;
        private final List<String> queuedKeys;
        private final List<QueueEntry> entries;

        public QueueState(int queuedCount, boolean countingDown, int secondsRemaining, int duplicateSuppressions) {
            this(queuedCount, countingDown, false, secondsRemaining, duplicateSuppressions, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        public QueueState(int queuedCount, boolean countingDown, boolean paused, int secondsRemaining, int duplicateSuppressions, List<ViolationRecord> queuedRecords, List<String> queuedKeys, List<QueueEntry> entries) {
            this.queuedCount = queuedCount;
            this.countingDown = countingDown;
            this.paused = paused;
            this.secondsRemaining = secondsRemaining;
            this.duplicateSuppressions = duplicateSuppressions;
            this.queuedRecords = new ArrayList<>(queuedRecords);
            this.queuedKeys = new ArrayList<>(queuedKeys);
            this.entries = new ArrayList<>(entries);
        }

        public int getQueuedCount() { return queuedCount; }
        public boolean isCountingDown() { return countingDown; }
        public boolean isPaused() { return paused; }
        public int getSecondsRemaining() { return secondsRemaining; }
        public int getDuplicateSuppressions() { return duplicateSuppressions; }
        public int size() { return queuedCount; }
        public boolean countingDown() { return countingDown; }
        public boolean paused() { return paused; }
        public int secondsRemaining() { return secondsRemaining; }
        public int duplicateRejectCount() { return duplicateSuppressions; }
        public List<ViolationRecord> queuedRecords() { return new ArrayList<>(queuedRecords); }
        public List<String> queuedKeys() { return new ArrayList<>(queuedKeys); }
        public List<QueueEntry> entries() { return new ArrayList<>(entries); }
    }
}
