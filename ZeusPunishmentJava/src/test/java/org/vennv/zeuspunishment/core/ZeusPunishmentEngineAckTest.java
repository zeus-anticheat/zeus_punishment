package org.vennv.zeuspunishment.core;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.config.ActionType;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.Severity;
import org.vennv.zeuspunishment.core.model.ViolationLog;
import org.vennv.zeuspunishment.core.model.ViolationRecord;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZeusPunishmentEngineAckTest {
    @Test
    void executedOutcomeAcknowledgesAfterDispatch() throws Exception {
        PunishmentConfig config = config(ActionType.KICK);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingApiClient api = new RecordingApiClient();
        ZeusPunishmentEngine engine = engine(config, dispatcher, api, new BanwaveManager(config, dispatcher));

        process(engine, record("ack-1", "PlayerOne"));

        assertEquals(1, dispatcher.kicks);
        assertEquals(1, api.ackCalls);
        assertEquals("ack-1", api.lastUid);
    }

    @Test
    void queuedOutcomeAcknowledgesAfterQueueAcceptance() throws Exception {
        PunishmentConfig config = config(ActionType.BAN);
        config.setBanwaveEnabled(true);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingApiClient api = new RecordingApiClient();
        ZeusPunishmentEngine engine = engine(config, dispatcher, api, new BanwaveManager(config, dispatcher));

        process(engine, record("ack-2", "PlayerTwo"));

        assertEquals(1, api.ackCalls);
        assertEquals("ack-2", api.lastUid);
    }

    @Test
    void ignoredAndFailuresDoNotAcknowledge() throws Exception {
        assertNoAck(DispatcherOutcome.ignored("ignored"));
        assertNoAck(DispatcherOutcome.retryableFailure("retry later"));
        assertNoAck(DispatcherOutcome.permanentFailure("permanent"));
    }

    private static void assertNoAck(DispatcherOutcome outcome) throws Exception {
        PunishmentConfig config = config(ActionType.KICK);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        dispatcher.kickOutcome = outcome;
        RecordingApiClient api = new RecordingApiClient();
        ZeusPunishmentEngine engine = engine(config, dispatcher, api, new BanwaveManager(config, dispatcher));

        process(engine, record("ack-none-" + outcome.status(), "PlayerNoAck"));

        assertEquals(1, dispatcher.kicks);
        assertEquals(0, api.ackCalls);
    }

    private static PunishmentConfig config(ActionType action) {
        PunishmentConfig config = new PunishmentConfig();
        config.setActionForModel("profile", Severity.NORMAL, action);
        return config;
    }

    private static ZeusPunishmentEngine engine(PunishmentConfig config, RecordingDispatcher dispatcher, ZeusApiClient api, BanwaveManager manager) throws Exception {
        ZeusPunishmentEngine engine = new ZeusPunishmentEngine(config, dispatcher, api, manager);
        Field enforcement = ZeusPunishmentEngine.class.getDeclaredField("enforcementEnabled");
        enforcement.setAccessible(true);
        enforcement.set(engine, true);
        return engine;
    }

    private static boolean process(ZeusPunishmentEngine engine, ViolationRecord record) throws Exception {
        Method method = ZeusPunishmentEngine.class.getDeclaredMethod("processRecord", ViolationRecord.class);
        method.setAccessible(true);
        return (boolean) method.invoke(engine, record);
    }

    private static ViolationRecord record(String uid, String username) {
        return new ViolationRecord(uid, username, 1, 10.0f, Severity.NORMAL,
                Collections.singletonList(new ViolationLog(1L, "profile", "check", 50, 10.0f, "Policy evaluation")));
    }

    private static final class RecordingApiClient extends ZeusApiClient {
        int ackCalls;
        String lastUid;

        RecordingApiClient() {
            super("http://127.0.0.1:9");
        }

        @Override
        public boolean acknowledgeViolations(List<String> uids) {
            ackCalls++;
            lastUid = uids == null || uids.isEmpty() ? null : uids.get(0);
            return true;
        }
    }

    private static final class RecordingDispatcher implements PunishmentDispatcher {
        int kicks;
        DispatcherOutcome kickOutcome = DispatcherOutcome.executed("kick scheduled");

        @Override
        public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) {
            kicks++;
            return kickOutcome;
        }

        @Override
        public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) {
            return DispatcherOutcome.executed("ban scheduled");
        }

        @Override
        public DispatcherOutcome setbackPlayer(ViolationRecord record) {
            return DispatcherOutcome.executed("setback scheduled");
        }

        @Override
        public DispatcherOutcome mitigatePlayer(ViolationRecord record) {
            return DispatcherOutcome.executed("mitigation scheduled");
        }

        @Override
        public DispatcherOutcome playEffect(String uid) {
            return DispatcherOutcome.executed("effect scheduled");
        }

        @Override
        public DispatcherOutcome broadcast(String message) {
            return DispatcherOutcome.executed("broadcast scheduled");
        }

        @Override
        public DispatcherOutcome logVerbose(String message) {
            return DispatcherOutcome.executed("verbose log scheduled");
        }
    }
}
