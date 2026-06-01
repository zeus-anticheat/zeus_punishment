package org.vennv.zeuspunishment.core.model;

public final class DispatcherOutcome {
    public enum Status { EXECUTED, QUEUED, IGNORED, RETRYABLE_FAILURE, PERMANENT_FAILURE }

    private final Status status;
    private final String message;

    private DispatcherOutcome(Status status, String message) {
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public static DispatcherOutcome executed(String message) { return new DispatcherOutcome(Status.EXECUTED, message); }
    public static DispatcherOutcome queued(String message) { return new DispatcherOutcome(Status.QUEUED, message); }
    public static DispatcherOutcome ignored(String message) { return new DispatcherOutcome(Status.IGNORED, message); }
    public static DispatcherOutcome retryableFailure(String message) { return new DispatcherOutcome(Status.RETRYABLE_FAILURE, message); }
    public static DispatcherOutcome permanentFailure(String message) { return new DispatcherOutcome(Status.PERMANENT_FAILURE, message); }

    public Status status() { return status; }
    public String message() { return message; }
    public boolean isSuccessfulAcceptance() { return status == Status.EXECUTED || status == Status.QUEUED; }
}
