package org.vennv.zeuspunishment.core.audit;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

@FunctionalInterface
public interface AuditSink {
    void record(AuditEvent event);

    static AuditSink loggerSummary(Logger logger) {
        return loggerSummary(logger, summary -> logger.info(summary));
    }

    static AuditSink loggerSummary(Logger logger, Consumer<String> observer) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(observer, "observer");
        return event -> {
            String summary = event.toSummaryString();
            logger.info(summary);
            observer.accept(summary);
        };
    }
}
