package org.vennv.zeuspunishment.core.scheduler;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.Severity;
import org.vennv.zeuspunishment.core.model.ViolationKey;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class BanwaveManagerTest {
    @Test
    void duplicateQueueUsesStableKeyAndTracksRejection() {
        BanwaveManager manager = new BanwaveManager(new PunishmentConfig(), new RecordingDispatcher());
        ViolationRecord first = record("uid-1", "PlayerOne");
        ViolationRecord duplicate = record("UID-1", "playerone");

        assertEquals(DispatcherOutcome.Status.QUEUED, manager.queueBan(first).status());
        assertEquals(DispatcherOutcome.Status.IGNORED, manager.queueBan(duplicate).status());

        BanwaveManager.QueueState state = manager.getQueueState();
        assertEquals(1, state.size());
        assertEquals(1, state.duplicateRejectCount());
        assertTrue(state.queuedKeys().contains(ViolationKey.from(first).asString()));
        assertTrue(manager.isQueued(ViolationKey.from(duplicate)));
    }

    @Test
    void queueStateExposesCountdownAndQueuedRecords() {
        PunishmentConfig config = new PunishmentConfig();
        config.setBanwaveCountdownStartSeconds(30);
        BanwaveManager manager = new BanwaveManager(config, new RecordingDispatcher());
        manager.queueBan(record("uid-2", "PlayerTwo"));
        manager.startCountdown();

        BanwaveManager.QueueState state = manager.getQueueState();
        assertEquals(1, state.size());
        assertEquals(1, state.queuedRecords().size());
        assertTrue(state.countingDown());
        assertEquals(30, state.secondsRemaining());
    }

    @Test
    void executeBanwaveDispatchesUniqueRecordsAndClearsQueue() {
        PunishmentConfig config = new PunishmentConfig();
        config.setBanwaveCountdownStartSeconds(0);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        BanwaveManager manager = new BanwaveManager(config, dispatcher);
        manager.queueBan(record("uid-3", "PlayerThree"));
        manager.queueBan(record("uid-3", "PlayerThree"));
        manager.startCountdown();

        manager.tickSecond();

        assertEquals(1, dispatcher.bans);
        assertEquals(1, dispatcher.effects);
        assertEquals(1, dispatcher.broadcasts);
        assertEquals(0, manager.getQueueState().size());
    }

    private static ViolationRecord record(String uid, String username) {
        return new ViolationRecord(uid, username, 1, 10.0f, Severity.BAN, Collections.emptyList());
    }

    private static final class RecordingDispatcher implements PunishmentDispatcher {
        int bans;
        int effects;
        int broadcasts;

        @Override
        public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) { return DispatcherOutcome.executed("kick"); }

        @Override
        public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) {
            bans++;
            return DispatcherOutcome.executed("ban");
        }

        @Override
        public DispatcherOutcome setbackPlayer(ViolationRecord record) { return DispatcherOutcome.executed("setback"); }

        @Override
        public DispatcherOutcome mitigatePlayer(ViolationRecord record) { return DispatcherOutcome.executed("mitigation"); }

        @Override
        public DispatcherOutcome playEffect(String uid) {
            effects++;
            return DispatcherOutcome.executed("effect");
        }

        @Override
        public DispatcherOutcome broadcast(String message) {
            broadcasts++;
            return DispatcherOutcome.executed("broadcast");
        }

        @Override
        public DispatcherOutcome logVerbose(String message) { return DispatcherOutcome.executed("log"); }
    }
}
