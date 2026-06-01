package org.vennv.zeuspunishment.core.scheduler;

import org.vennv.zeuspunishment.core.PunishmentDispatcher;
import org.vennv.zeuspunishment.core.config.PunishmentConfig;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.util.ArrayList;
import java.util.List;

public class BanwaveManager {
    private final List<ViolationRecord> queuedBans = new ArrayList<>();
    private final PunishmentConfig config;
    private final PunishmentDispatcher dispatcher;

    private boolean isCountingDown = false;
    private int secondsRemaining = 0;

    public BanwaveManager(PunishmentConfig config, PunishmentDispatcher dispatcher) {
        this.config = config;
        this.dispatcher = dispatcher;
    }

    public synchronized void queueBan(ViolationRecord record) {
        queuedBans.add(record);
    }

    public synchronized boolean isQueued(String uid) {
        for (ViolationRecord record : queuedBans) {
            if (record.getUid().equals(uid)) return true;
        }
        return false;
    }

    public synchronized void startCountdown() {
        if (isCountingDown) return;
        if (queuedBans.isEmpty()) return;

        isCountingDown = true;
        secondsRemaining = config.getBanwaveCountdownStartSeconds();
    }

    public synchronized void clearQueue() {
        queuedBans.clear();
        isCountingDown = false;
        secondsRemaining = 0;
    }

    /**
     * Called every second by the platform-specific scheduler (Bukkit/Fabric).
     */
    public synchronized void tickSecond() {
        if (!isCountingDown) return;

        if (secondsRemaining > 0) {
            // Broadcast countdown at specific intervals
            if (secondsRemaining == 60) {
                dispatcher.broadcast("&e[Zeus] &cBanwave incoming in 1 minute!");
            } else if (secondsRemaining == 30) {
                dispatcher.broadcast("&e[Zeus] &cBanwave incoming in 30 seconds!");
            } else if (secondsRemaining <= 5) {
                dispatcher.broadcast("&e[Zeus] &cBanwave in " + secondsRemaining + "...");
            }
            secondsRemaining--;
        } else {
            // Execution time!
            executeBanwave();
        }
    }

    private void executeBanwave() {
        int count = queuedBans.size();
        for (ViolationRecord record : queuedBans) {
            // Ban the player
            dispatcher.banPlayer(record, "Zeus Banwave", 0L);
            dispatcher.playEffect(record.getUid());

            // Clear from platform via API delete? 
            // In a real scenario, the polling cycle handles deletion, or we can do it directly.
        }

        // Summary Broadcast
        String summaryMsg = config.getBanwaveSummary().replace("%count%", String.valueOf(count));
        dispatcher.broadcast(summaryMsg);

        clearQueue();
    }

    public synchronized List<ViolationRecord> getQueuedBans() {
        return new ArrayList<>(queuedBans);
    }
}
