package org.vennv.zeuspunishment.core.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AuditEvent(
        Instant timestamp,
        String eventType,
        String stableId,
        String playerId,
        String policyTier,
        String policyAction,
        String dispatcherOutcome,
        String queueResult,
        String ackResult,
        Boolean cooldownAllowed,
        String cooldownCategory,
        int cooldownSuppressionCount,
        boolean dryRun,
        boolean highImpactAllowed,
        String outcome,
        boolean retryable
) {
    public AuditEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        eventType = safe(eventType, "event");
        stableId = safe(stableId, "unknown");
        playerId = safe(playerId, "unknown");
        policyTier = safe(policyTier, "observe");
        policyAction = safe(policyAction, "LOG");
        dispatcherOutcome = safe(dispatcherOutcome, "none");
        queueResult = safe(queueResult, "none");
        ackResult = safe(ackResult, "none");
        cooldownCategory = safe(cooldownCategory, "none");
        outcome = safe(outcome, "recorded");
    }

    public static Builder builder(String eventType) {
        return new Builder(eventType);
    }

    public String toJsonLine() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", timestamp.toString());
        fields.put("eventType", eventType);
        fields.put("stableId", stableId);
        fields.put("playerId", playerId);
        fields.put("policyTier", policyTier);
        fields.put("policyAction", policyAction);
        fields.put("dispatcherOutcome", dispatcherOutcome);
        fields.put("queueResult", queueResult);
        fields.put("ackResult", ackResult);
        fields.put("cooldownAllowed", cooldownAllowed);
        fields.put("cooldownCategory", cooldownCategory);
        fields.put("cooldownSuppressionCount", cooldownSuppressionCount);
        fields.put("dryRun", dryRun);
        fields.put("highImpactAllowed", highImpactAllowed);
        fields.put("outcome", outcome);
        fields.put("retryable", retryable);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return json.append('}').toString();
    }

    public String toSummaryString() {
        return "event=" + eventType
                + " stableId=" + stableId
                + " tier=" + policyTier
                + " action=" + policyAction
                + " outcome=" + outcome
                + " dispatcher=" + dispatcherOutcome
                + " queue=" + queueResult
                + " ack=" + ackResult
                + " cooldown=" + (cooldownAllowed == null ? "n/a" : cooldownAllowed);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escape(String value) {
        return Objects.toString(value, "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private final String eventType;
        private String stableId;
        private String playerId;
        private String policyTier;
        private String policyAction;
        private String dispatcherOutcome;
        private String queueResult;
        private String ackResult;
        private Boolean cooldownAllowed;
        private String cooldownCategory;
        private int cooldownSuppressionCount;
        private boolean dryRun;
        private boolean highImpactAllowed;
        private String outcome;
        private boolean retryable;

        private Builder(String eventType) { this.eventType = eventType; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder stableId(String stableId) { this.stableId = stableId; return this; }
        public Builder playerId(String playerId) { this.playerId = playerId; return this; }
        public Builder policyTier(String policyTier) { this.policyTier = policyTier; return this; }
        public Builder policyAction(String policyAction) { this.policyAction = policyAction; return this; }
        public Builder dispatcherOutcome(String dispatcherOutcome) { this.dispatcherOutcome = dispatcherOutcome; return this; }
        public Builder queueResult(String queueResult) { this.queueResult = queueResult; return this; }
        public Builder ackResult(String ackResult) { this.ackResult = ackResult; return this; }
        public Builder cooldownAllowed(Boolean cooldownAllowed) { this.cooldownAllowed = cooldownAllowed; return this; }
        public Builder cooldownCategory(String cooldownCategory) { this.cooldownCategory = cooldownCategory; return this; }
        public Builder cooldownSuppressionCount(int cooldownSuppressionCount) { this.cooldownSuppressionCount = cooldownSuppressionCount; return this; }
        public Builder dryRun(boolean dryRun) { this.dryRun = dryRun; return this; }
        public Builder highImpactAllowed(boolean highImpactAllowed) { this.highImpactAllowed = highImpactAllowed; return this; }
        public Builder outcome(String outcome) { this.outcome = outcome; return this; }
        public Builder retryable(boolean retryable) { this.retryable = retryable; return this; }
        public AuditEvent build() { return new AuditEvent(timestamp, eventType, stableId, playerId, policyTier, policyAction, dispatcherOutcome, queueResult, ackResult, cooldownAllowed, cooldownCategory, cooldownSuppressionCount, dryRun, highImpactAllowed, outcome, retryable); }
    }
}
