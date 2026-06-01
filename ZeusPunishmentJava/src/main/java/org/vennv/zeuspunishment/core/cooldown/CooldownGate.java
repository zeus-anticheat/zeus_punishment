package org.vennv.zeuspunishment.core.cooldown;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public class CooldownGate {
    public enum Category {
        PUNISHMENT,
        BROADCAST,
        EFFECT,
        STATUS_REFRESH,
        GUI_REFRESH
    }

    public record Decision(
            boolean allowed,
            String stableKey,
            Category category,
            long windowMillis,
            long remainingMillis,
            int suppressionCount
    ) { }

    private final LongSupplier clockMillis;
    private final Map<String, Entry> entries = new HashMap<>();

    public CooldownGate() {
        this(System::currentTimeMillis);
    }

    public CooldownGate(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public synchronized Decision check(String playerKey, String violationKey, Category category, Duration window) {
        String stableKey = stableKey(playerKey, violationKey, category);
        long now = clockMillis.getAsLong();
        long windowMillis = Math.max(0L, window.toMillis());
        Entry entry = entries.get(stableKey);
        if (entry == null || now >= entry.allowedAfterMillis || windowMillis == 0L) {
            entries.put(stableKey, new Entry(now + windowMillis, 0));
            return new Decision(true, stableKey, category, windowMillis, 0L, 0);
        }
        entry.suppressionCount++;
        long remaining = Math.max(0L, entry.allowedAfterMillis - now);
        return new Decision(false, stableKey, category, windowMillis, remaining, entry.suppressionCount);
    }

    private static String stableKey(String playerKey, String violationKey, Category category) {
        String safePlayer = playerKey == null ? "" : playerKey;
        String safeViolation = violationKey == null ? "" : violationKey;
        return safePlayer + ":" + safeViolation + ":" + category.name();
    }

    private static class Entry {
        private final long allowedAfterMillis;
        private int suppressionCount;

        private Entry(long allowedAfterMillis, int suppressionCount) {
            this.allowedAfterMillis = allowedAfterMillis;
            this.suppressionCount = suppressionCount;
        }
    }
}
