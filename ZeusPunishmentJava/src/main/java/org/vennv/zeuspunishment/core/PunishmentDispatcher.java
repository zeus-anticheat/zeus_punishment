package org.vennv.zeuspunishment.core;

import org.vennv.zeuspunishment.core.model.ViolationRecord;

public interface PunishmentDispatcher {
    /**
     * Executes a kick punishment.
     */
    void kickPlayer(ViolationRecord record, String reason);

    /**
     * Executes a ban punishment.
     */
    void banPlayer(ViolationRecord record, String reason, long durationMillis);

    /**
     * Setbacks the player to their client location.
     */
    void setbackPlayer(ViolationRecord record);

    /**
     * Mitigates the player's movement without teleporting them.
     */
    void mitigatePlayer(ViolationRecord record);

    /**
     * Plays the custom Zeus bolt effect at the player's location.
     */
    void playEffect(String uid);

    /**
     * Broadcasts a message to the entire server.
     */
    void broadcast(String message);

    /**
     * Send a message to admins/developers with verbosity enabled.
     */
    void logVerbose(String message);
}
