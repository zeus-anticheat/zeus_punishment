package org.vennv.zeuspunishment.core.model;

import java.util.Objects;

public final class ViolationKey {
    private final String uid;
    private final Severity severity;
    private final String profile;

    private ViolationKey(String uid, Severity severity, String profile) {
        this.uid = normalize(uid);
        this.severity = severity;
        this.profile = profile == null ? "default" : profile.trim().toLowerCase();
    }

    public static ViolationKey from(ViolationRecord record) {
        String profile = "default";
        if (record != null) {
            for (ViolationLog log : record.getLogs()) {
                if (log.getModelId() != null && !log.getModelId().isBlank()) { profile = log.getModelId(); break; }
            }
        }
        return new ViolationKey(record == null ? "" : record.getUid(), record == null ? null : record.getSeverity(), profile);
    }

    public String asString() {
        return uid + ":" + (severity == null ? "unknown" : severity.name().toLowerCase()) + ":" + profile;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    @Override public String toString() { return asString(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViolationKey)) return false;
        ViolationKey that = (ViolationKey) o;
        return Objects.equals(uid, that.uid) && severity == that.severity && Objects.equals(profile, that.profile);
    }
    @Override public int hashCode() { return Objects.hash(uid, severity, profile); }
}
