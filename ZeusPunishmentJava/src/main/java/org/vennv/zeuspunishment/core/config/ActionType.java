package org.vennv.zeuspunishment.core.config;

public enum ActionType {
    BAN,
    KICK,
    SETBACK,
    MITIGATE,
    NONE;

    public static ActionType fromString(String str) {
        if (str == null) return NONE;
        String upper = str.toUpperCase();
        if (upper.startsWith("BAN")) return BAN;
        if (upper.startsWith("KICK")) return KICK;
        if (upper.startsWith("SETBACK")) return SETBACK;
        if (upper.startsWith("MITIGATE")) return MITIGATE;
        return NONE;
    }
}
