package org.vennv.zeuspunishment.core;

import org.junit.jupiter.api.Test;
import org.vennv.zeuspunishment.core.audit.AuditEvent;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.cooldown.CooldownGate;
import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.Severity;
import org.vennv.zeuspunishment.core.model.ViolationLog;
import org.vennv.zeuspunishment.core.model.ViolationRecord;
import org.vennv.zeuspunishment.core.network.ZeusApiClient;
import org.vennv.zeuspunishment.core.scheduler.BanwaveManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZeusPunishmentEnginePolicyTest {
    @Test
    void dryRunRecordsWouldActWithoutDispatcherMutation() {
        Fixture fixture = fixture();
        fixture.config.setPolicyPreset("kick");
        fixture.config.setDryRun(true);
        fixture.config.setEnforcementEnabled(false);

        assertTrue(fixture.engine.processRecord(record(Severity.KICK)));

        assertEquals(0, fixture.dispatcher.mutations);
        assertTrue(fixture.audit.events.stream().anyMatch(event -> event.dryRun() && "would-act".equals(event.outcome())));
    }

    @Test
    void dispatcherOutcomeAndAcknowledgementAreAudited() {
        Fixture fixture = fixture();
        fixture.config.setPolicyPreset("kick");
        fixture.config.setDryRun(false);
        fixture.config.setEnforcementEnabled(true);
        fixture.config.setBroadcastsEnabled(true);
        fixture.config.setEffectsEnabled(true);

        assertTrue(fixture.engine.processRecord(record(Severity.KICK)));

        assertTrue(fixture.audit.events.stream().anyMatch(event -> "applied".equals(event.outcome())
                && "dispatched".equals(event.dispatcherOutcome())
                && "acknowledged".equals(event.ackResult())
                && event.cooldownAllowed() != null));
    }

    @Test
    void repeatedEventSuppressedByCooldownIsAudited() {
        Fixture fixture = fixture();
        fixture.config.setPolicyPreset("kick");
        fixture.config.setDryRun(false);
        fixture.config.setEnforcementEnabled(true);
        fixture.config.setPunishmentCooldownSeconds(60);

        fixture.engine.processRecord(record(Severity.KICK));
        fixture.engine.processRecord(record(Severity.KICK));

        assertEquals(1, fixture.dispatcher.kicks);
        assertTrue(fixture.audit.events.stream().anyMatch(event -> "cooldown".equals(event.eventType())
                && "suppressed".equals(event.outcome())
                && event.cooldownSuppressionCount() >= 1));
    }

    @Test
    void highImpactDisabledSuppressesImmediateBanBroadcastAndEffect() {
        Fixture fixture = fixture();
        fixture.config.setPolicyPreset("enforce");
        fixture.config.setDryRun(false);
        fixture.config.setEnforcementEnabled(true);
        fixture.config.setImmediateBanEnabled(false);
        fixture.config.setBroadcastsEnabled(false);
        fixture.config.setEffectsEnabled(false);

        assertTrue(fixture.engine.processRecord(record(Severity.BAN)));

        assertEquals(0, fixture.dispatcher.bans);
        assertEquals(0, fixture.dispatcher.broadcasts);
        assertEquals(0, fixture.dispatcher.effects);
        assertTrue(fixture.audit.events.stream().anyMatch(event -> !event.highImpactAllowed() && "would-act".equals(event.outcome())));
    }

    @Test
    void gatewayStyleCompositeSinkWritesJsonl() throws Exception {
        Path dataFolder = Files.createTempDirectory("gateway-audit");
        var sink = ZeusPunishmentEngine.createDefaultAuditSink(dataFolder, java.util.logging.Logger.getLogger("gateway"), true);
        sink.record(AuditEvent.builder("decision").stableId("stable").outcome("applied").build());
        assertTrue(Files.exists(dataFolder.resolve("logs/zeus-punishment-audit.jsonl")));
    }

    private static Fixture fixture() {
        PunishmentConfig config = new PunishmentConfig();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        RecordingAudit audit = new RecordingAudit();
        BanwaveManager banwave = new BanwaveManager(config, dispatcher);
        ZeusPunishmentEngine engine = new ZeusPunishmentEngine(config, dispatcher, new ZeusApiClient("http://127.0.0.1"), banwave, audit, new CooldownGate(() -> 1000L));
        return new Fixture(config, dispatcher, audit, engine);
    }

    private static ViolationRecord record(Severity severity) {
        return new ViolationRecord("uid-1", "PlayerOne", 1, 10.0f, severity,
                List.of(new ViolationLog(1L, "profile-a", "public", 0, 1.0f, null)));
    }

    private record Fixture(PunishmentConfig config, RecordingDispatcher dispatcher, RecordingAudit audit, ZeusPunishmentEngine engine) {}

    private static final class RecordingAudit implements org.vennv.zeuspunishment.core.audit.AuditSink {
        private final List<AuditEvent> events = new ArrayList<>();
        @Override public void record(AuditEvent event) { events.add(event); }
    }

    private static final class RecordingDispatcher implements PunishmentDispatcher {
        int mutations;
        int kicks;
        int bans;
        int broadcasts;
        int effects;
        @Override public DispatcherOutcome kickPlayer(ViolationRecord record, String reason) { mutations++; kicks++; return DispatcherOutcome.executed("kick"); }
        @Override public DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) { mutations++; bans++; return DispatcherOutcome.executed("ban"); }
        @Override public DispatcherOutcome setbackPlayer(ViolationRecord record) { mutations++; return DispatcherOutcome.executed("setback"); }
        @Override public DispatcherOutcome mitigatePlayer(ViolationRecord record) { mutations++; return DispatcherOutcome.executed("mitigate"); }
        @Override public DispatcherOutcome playEffect(String uid) { mutations++; effects++; return DispatcherOutcome.executed("effect"); }
        @Override public DispatcherOutcome broadcast(String message) { mutations++; broadcasts++; return DispatcherOutcome.executed("broadcast"); }
        @Override public DispatcherOutcome logVerbose(String message) { return DispatcherOutcome.executed("log"); }
    }
}
