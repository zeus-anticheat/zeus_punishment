package org.vennv.zeuspunishment.core.audit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CompositeAuditSink implements AuditSink {
    private final List<AuditSink> sinks;

    public CompositeAuditSink(AuditSink... sinks) {
        this.sinks = Arrays.stream(sinks == null ? new AuditSink[0] : sinks)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public void record(AuditEvent event) {
        for (AuditSink sink : sinks) {
            sink.record(event);
        }
    }
}
