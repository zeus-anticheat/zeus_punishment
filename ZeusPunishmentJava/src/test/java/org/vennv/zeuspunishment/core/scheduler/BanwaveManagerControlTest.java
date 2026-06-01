package org.vennv.zeuspunishment.core.scheduler;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.audit.AuditEvent;
import org.vennv.zeuspunishment.core.audit.AuditSink;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.cooldown.CooldownGate;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.Severity;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BanwaveManagerControlTest {
    @Test
    void queueRequiresManualApprovalByDefault() {
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        BanwaveManager manager = new BanwaveManager(new PunishmentConfig(), dispatcher);

        assertEquals(DispatcherOutcome.Status.QUEUED, manager.queueBan(record("uid-1", "PlayerOne")).status());

        BanwaveManager.QueueState state = manager.getQueueState();
        assertEquals(1, state.size());
        assertFalse(state.countingDown());
        assertEquals(0, dispatcher.bans);
    }

    @Test
    void listDetailsCancelPauseResumeAndClearMutateQueue() {
        PunishmentConfig config = new PunishmentConfig();
        config.setBanwaveCountdownStartSeconds(10);
        BanwaveManager manager = new BanwaveManager(config, new RecordingDispatcher());
        manager.queueBan(record("uid-2", "PlayerTwo"));
        String key = manager.getQueueState().queuedKeys().get(0);

        assertEquals(1, manager.list().size());
        assertNotNull(manager.details(key));
        assertTrue(manager.resume().success());
        assertTrue(manager.getQueueState().countingDown());
        assertTrue(manager.pause().success());
        assertTrue(manager.getQueueState().paused());
        assertTrue(manager.cancel(key).success());
        assertEquals(0, manager.getQueueState().size());

        manager.queueBan(record("uid-3", "PlayerThree"));
        assertTrue(manager.clearQueue().success());
        assertEquals(0, manager.getQueueState().size());
    }

    @Test
    void executeAllUsesDispatcherCooldownAndAudit() {
        AtomicLong clock = new AtomicLong(1000L);
        PunishmentConfig config = new PunishmentConfig();
        config.setPunishmentCooldownSeconds(30);
        config.setBroadcastCooldownSeconds(30);
        config.setEffectCooldownSeconds(30);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingAudit audit = new RecordingAudit();
        BanwaveManager manager = new BanwaveManager(config, dispatcher, audit, new CooldownGate(clock::get));

        manager.queueBan(record("uid-4", "PlayerFour"));
        assertTrue(manager.executeAll().success());

        assertEquals(1, dispatcher.bans);
        assertEquals(1, dispatcher.effects);
        assertEquals(1, dispatcher.broadcasts);
        assertTrue(audit.events.stream().anyMatch(e -> e.eventType().equals("banwave-execute")));
        assertEquals(0, manager.getQueueState().size());
    }

    private static ViolationRecord record(String uid, String username) {
        return new ViolationRecord(uid, username, 1, 10.0f, Severity.BAN, Collections.emptyList());
    }

    private static final class RecordingAudit implements AuditSink {
        final List<AuditEvent> events = new ArrayList<>();
        @Override public void record(AuditEvent event) { events.add(event); }
    }

    private static final class RecordingDispatcher implements PunishmentDispatcher {
        int bans;
        int effects;
        int broadcasts;
        @Override public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) { return DispatcherOutcome.executed("kick"); }
        @Override public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) { bans++; return DispatcherOutcome.executed("ban"); }
        @Override public DispatcherOutcome setbackPlayer(ViolationRecord record) { return DispatcherOutcome.executed("setback"); }
        @Override public DispatcherOutcome mitigatePlayer(ViolationRecord record) { return DispatcherOutcome.executed("mitigation"); }
        @Override public DispatcherOutcome playEffect(String uid) { effects++; return DispatcherOutcome.executed("effect"); }
        @Override public DispatcherOutcome broadcast(String message) { broadcasts++; return DispatcherOutcome.executed("broadcast"); }
        @Override public DispatcherOutcome logVerbose(String message) { return DispatcherOutcome.executed("log"); }
    }
}
