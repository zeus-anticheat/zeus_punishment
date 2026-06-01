package org.vennv.zeuspunishment.core.audit;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AuditSinkTest {
    @Test
    void fileAuditSinkAppendsJsonLinesUnderPluginLogsFolder() throws Exception {
        Path dataFolder = Files.createTempDirectory("zeus-audit");
        FileAuditSink sink = FileAuditSink.forDataFolder(dataFolder);
        sink.record(sampleEvent("decision", "applied"));

        Path auditFile = dataFolder.resolve("logs").resolve("zeus-punishment-audit.jsonl");
        assertTrue(Files.exists(auditFile));
        List<String> lines = Files.readAllLines(auditFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("{"));
        assertTrue(lines.get(0).contains("\"eventType\":\"decision\""));
        assertTrue(lines.get(0).contains("\"policyTier\":\"review\""));
        assertTrue(lines.get(0).contains("\"dispatcherOutcome\":\"queued\""));
        assertTrue(lines.get(0).contains("\"ackResult\":\"acknowledged\""));
        assertTrue(lines.get(0).contains("\"cooldownAllowed\":true"));
    }

    @Test
    void compositeWritesToFileAndLoggerAndNoopAcceptsRecords() throws Exception {
        Path dataFolder = Files.createTempDirectory("zeus-audit-composite");
        FileAuditSink fileSink = FileAuditSink.forDataFolder(dataFolder);
        List<String> summaries = new ArrayList<>();
        AuditSink loggerSink = AuditSink.loggerSummary(Logger.getLogger("test"), summaries::add);
        CompositeAuditSink composite = new CompositeAuditSink(fileSink, loggerSink, NoopAuditSink.INSTANCE);

        composite.record(sampleEvent("cooldown", "suppressed"));

        assertEquals(1, Files.readAllLines(dataFolder.resolve("logs/zeus-punishment-audit.jsonl")).size());
        assertEquals(1, summaries.size());
        assertTrue(summaries.get(0).contains("event=cooldown"));
        assertTrue(summaries.get(0).contains("outcome=suppressed"));
    }

    @Test
    void auditOutputDoesNotExposeImplementationTerminology() throws Exception {
        Path dataFolder = Files.createTempDirectory("zeus-audit-safe");
        FileAuditSink sink = FileAuditSink.forDataFolder(dataFolder);
        sink.record(sampleEvent("decision", "applied"));
        String json = Files.readString(dataFolder.resolve("logs/zeus-punishment-audit.jsonl"));
        String summary = sampleEvent("decision", "applied").toSummaryString();
        String combined = json + "\n" + summary;
        assertFalse(combined.matches("(?s).*(LSTM|FFN|RCF|Bi-LSTM|Random Cut Forest|model scores|AI explanations|bypass|check internals|/api/network).*"));
    }

    private static AuditEvent sampleEvent(String type, String outcome) {
        return AuditEvent.builder(type)
                .stableId("player-1:violation-1")
                .playerId("player-1")
                .policyTier("review")
                .policyAction("BANWAVE")
                .dispatcherOutcome("queued")
                .queueResult("queued")
                .ackResult("acknowledged")
                .cooldownAllowed(true)
                .cooldownCategory("PUNISHMENT")
                .cooldownSuppressionCount(0)
                .dryRun(false)
                .highImpactAllowed(true)
                .outcome(outcome)
                .retryable(false)
                .build();
    }
}
