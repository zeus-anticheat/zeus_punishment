package org.vennv.zeuspunishment.core.model;

public enum Severity {
    NORMAL(0),
    WARNING(1),
    KICK(2),
    BAN(3);

    private final int level;

    Severity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static Severity fromString(String severityString) {
        if (severityString == null) return NORMAL;
        switch (severityString.toUpperCase()) {
            case "WARNING": return WARNING;
            case "KICK": return KICK;
            case "BAN": return BAN;
            default: return NORMAL;
        }
    }
}
