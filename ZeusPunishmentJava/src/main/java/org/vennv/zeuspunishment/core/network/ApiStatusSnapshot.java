package org.vennv.zeuspunishment.core.network;

public final class ApiStatusSnapshot {
    private final boolean apiHealthy;
    private final boolean streamRunning;
    private final boolean reconnecting;
    private final long generation;
    private final int currentBackoffMs;
    private final String lastError;

    public ApiStatusSnapshot(boolean apiHealthy, boolean streamRunning, boolean reconnecting, long generation, int currentBackoffMs, String lastError) {
        this.apiHealthy = apiHealthy;
        this.streamRunning = streamRunning;
        this.reconnecting = reconnecting;
        this.generation = generation;
        this.currentBackoffMs = currentBackoffMs;
        this.lastError = lastError == null ? "none" : lastError;
    }

    public boolean isApiHealthy() { return apiHealthy; }
    public boolean isStreamRunning() { return streamRunning; }
    public boolean isReconnecting() { return reconnecting; }
    public long getGeneration() { return generation; }
    public int getCurrentBackoffMs() { return currentBackoffMs; }
    public String getLastError() { return lastError; }
}
