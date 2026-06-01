package org.vennv.zeuspunishment.core;

import org.vennv.zeuspunishment.core.model.DispatcherOutcome;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

public interface PunishmentDispatcher {
    /**
     * Executes a kick punishment.
     */
    default DispatcherOutcome kickPlayer(ViolationRecord record, String reason) { return DispatcherOutcome.executed("kick scheduled"); }

    /**
     * Executes a ban punishment.
     */
    default DispatcherOutcome banPlayer(ViolationRecord record, String reason, long durationMillis) { return DispatcherOutcome.executed("ban scheduled"); }

    /**
     * Setbacks the player to their client location.
     */
    default DispatcherOutcome setbackPlayer(ViolationRecord record) { return DispatcherOutcome.executed("setback scheduled"); }

    /**
     * Mitigates the player's movement without teleporting them.
     */
    default DispatcherOutcome mitigatePlayer(ViolationRecord record) { return DispatcherOutcome.executed("mitigation scheduled"); }

    /**
     * Plays the custom Zeus bolt effect at the player's location.
     */
    default DispatcherOutcome playEffect(String uid) { return DispatcherOutcome.executed("effect scheduled"); }

    /**
     * Broadcasts a message to the entire server.
     */
    default DispatcherOutcome broadcast(String message) { return DispatcherOutcome.executed("broadcast scheduled"); }

    /**
     * Send a message to admins/developers with verbosity enabled.
     */
    default DispatcherOutcome logVerbose(String message) { return DispatcherOutcome.executed("log recorded"); }
}
