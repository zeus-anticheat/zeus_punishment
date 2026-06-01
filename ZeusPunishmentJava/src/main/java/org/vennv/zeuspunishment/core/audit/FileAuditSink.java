package org.vennv.zeuspunishment.core.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileAuditSink implements AuditSink {
    public static final String DEFAULT_RELATIVE_PATH = "logs/zeus-punishment-audit.jsonl";

    private final Path auditFile;

    public FileAuditSink(Path auditFile) {
        this.auditFile = auditFile;
    }

    public static FileAuditSink forDataFolder(Path dataFolder) {
        return new FileAuditSink(dataFolder.resolve(DEFAULT_RELATIVE_PATH));
    }

    public Path auditFile() {
        return auditFile;
    }

    @Override
    public synchronized void record(AuditEvent event) {
        try {
            Path parent = auditFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(auditFile, event.toJsonLine() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to append punishment audit event", e);
        }
    }
}
