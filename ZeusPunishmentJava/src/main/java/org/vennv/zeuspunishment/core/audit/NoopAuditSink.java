package org.vennv.zeuspunishment.core.audit;

public enum NoopAuditSink implements AuditSink {
    INSTANCE;

    @Override
    public void record(AuditEvent event) {
        // Intentionally empty for disabled audit configurations and tests.
    }
}
