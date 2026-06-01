package org.vennv.zeuspunishment.core;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.config.ActionType;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.Severity;
import org.vennv.zeuspunishment.core.model.ViolationKey;
import org.vennv.zeuspunishment.core.model.ViolationLog;
import org.vennv.zeuspunishment.core.model.ViolationRecord;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZeusPunishmentEngineWorkflowTest {
    @Test
    void duplicateViolationKeySuppressesImmediateDuplicatePunishment() throws Exception {
        PunishmentConfig config = config(ActionType.KICK);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        ZeusPunishmentEngine engine = engine(config, dispatcher);
        ViolationRecord record = record("uid-1", "PlayerOne");

        assertTrue(process(engine, record));
        assertFalse(process(engine, record));

        assertEquals(1, dispatcher.kicks);
        assertEquals(1, processedSize(engine));
    }

    @Test
    void retryableFailureKeepsPendingKey() throws Exception {
        PunishmentConfig config = config(ActionType.KICK);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        dispatcher.kickOutcome = DispatcherOutcome.retryableFailure("scheduler unavailable");
        ZeusPunishmentEngine engine = engine(config, dispatcher);
        ViolationRecord record = record("uid-2", "PlayerTwo");

        assertFalse(process(engine, record));
        assertFalse(process(engine, record));

        assertEquals(1, dispatcher.kicks);
        assertEquals(1, pendingSize(engine));
    }

    @Test
    void banwaveQueueUsesViolationKeyAndDispatcherOutcome() throws Exception {
        PunishmentConfig config = config(ActionType.BAN);
        config.setBanwaveEnabled(true);
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        BanwaveManager manager = new BanwaveManager(config, dispatcher);
        ZeusPunishmentEngine engine = engine(config, dispatcher, manager);
        ViolationRecord record = record("uid-3", "PlayerThree");

        assertTrue(process(engine, record));
        assertTrue(manager.isQueued(ViolationKey.from(record)));
        assertEquals(1, manager.getQueueState().size());
    }

    @Test
    void dispatcherContractReturnsOutcomeStates() {
        assertEquals(DispatcherOutcome.Status.EXECUTED, DispatcherOutcome.executed("ok").status());
        assertEquals(DispatcherOutcome.Status.QUEUED, DispatcherOutcome.queued("ok").status());
        assertEquals(DispatcherOutcome.Status.IGNORED, DispatcherOutcome.ignored("ok").status());
        assertEquals(DispatcherOutcome.Status.RETRYABLE_FAILURE, DispatcherOutcome.retryableFailure("ok").status());
        assertEquals(DispatcherOutcome.Status.PERMANENT_FAILURE, DispatcherOutcome.permanentFailure("ok").status());
    }

    private static PunishmentConfig config(ActionType action) {
        PunishmentConfig config = new PunishmentConfig();
        config.setActionForModel("profile", Severity.NORMAL, action);
        return config;
    }

    private static ZeusPunishmentEngine engine(PunishmentConfig config, RecordingDispatcher dispatcher) throws Exception {
        return engine(config, dispatcher, new BanwaveManager(config, dispatcher));
    }

    private static ZeusPunishmentEngine engine(PunishmentConfig config, RecordingDispatcher dispatcher, BanwaveManager manager) throws Exception {
        ZeusPunishmentEngine engine = new ZeusPunishmentEngine(config, dispatcher, new AcknowledgingApiClient(), manager);
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

    private static int processedSize(ZeusPunishmentEngine engine) throws Exception {
        Field field = ZeusPunishmentEngine.class.getDeclaredField("processedViolations");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(engine)).size();
    }

    private static int pendingSize(ZeusPunishmentEngine engine) throws Exception {
        Field field = ZeusPunishmentEngine.class.getDeclaredField("pendingViolations");
        field.setAccessible(true);
        return ((java.util.Map<?, ?>) field.get(engine)).size();
    }

    private static ViolationRecord record(String uid, String username) {
        return new ViolationRecord(uid, username, 1, 10.0f, Severity.NORMAL,
                Collections.singletonList(new ViolationLog(1L, "profile", "check", 50, 10.0f, "Policy evaluation")));
    }

    private static final class AcknowledgingApiClient extends ZeusApiClient {
        AcknowledgingApiClient() {
            super("http://127.0.0.1:9");
        }

        @Override
        public boolean acknowledgeViolations(List<String> uids) {
            return true;
        }
    }

    private static final class RecordingDispatcher implements PunishmentDispatcher {
        int kicks;
        int bans;
        DispatcherOutcome kickOutcome = DispatcherOutcome.executed("kick scheduled");

        @Override
        public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) {
            kicks++;
            return kickOutcome;
        }

        @Override
        public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) {
            bans++;
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
