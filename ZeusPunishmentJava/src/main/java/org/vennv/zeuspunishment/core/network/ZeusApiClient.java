package org.vennv.zeuspunishment.core.network;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central Zeus Platform public API boundary for the punishment plugin.
 *
 * Contract:
 * - GET /api/public/violations is non-destructive and returns the current public violation list.
 * - GET /api/public/violations/stream returns Server-Sent Events where each "data:" value is JSON
 *   parsed by ViolationRecord.fromJson. Required fields: uid, username. Optional fields:
 *   warning_count, total_points, severity, and logs.
 * - GET /api/public/list_models returns a JSON array of configured detection profiles. Each entry may
 *   include an id used by punishment configuration.
 * - No allowed public acknowledgement/delete route exists in Phase 5; acknowledgement is unavailable
 *   and deferred to Phase 6/API-04.
 */
public class ZeusApiClient {
    public static final String PUBLIC_VIOLATIONS_PATH = "/api/public/violations";
    public static final String PUBLIC_VIOLATIONS_STREAM_PATH = "/api/public/violations/stream";
    public static final String PUBLIC_LIST_MODELS_PATH = "/api/public/list_models";

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RECONNECT_INITIAL_MS = 1000;
    private static final int DEFAULT_RECONNECT_MAX_MS = 30000;
    private static final long STOP_JOIN_MS = 3000L;

    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int reconnectInitialMs;
    private final int reconnectMaxMs;
    private final Object reconnectWaitMonitor = new Object();
    private final AtomicLong streamGeneration = new AtomicLong(0L);

    private volatile boolean streamRunning = false;
    private volatile Thread streamThread;
    private volatile HttpURLConnection activeConnection;
    private volatile BufferedReader activeReader;

    public ZeusApiClient(String baseUrl) {
        this(baseUrl, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, DEFAULT_RECONNECT_INITIAL_MS, DEFAULT_RECONNECT_MAX_MS);
    }

    public ZeusApiClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs, int reconnectInitialMs, int reconnectMaxMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.connectTimeoutMs = positiveOrDefault(connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS);
        this.readTimeoutMs = positiveOrDefault(readTimeoutMs, DEFAULT_READ_TIMEOUT_MS);
        this.reconnectInitialMs = positiveOrDefault(reconnectInitialMs, DEFAULT_RECONNECT_INITIAL_MS);
        this.reconnectMaxMs = Math.max(this.reconnectInitialMs, positiveOrDefault(reconnectMaxMs, DEFAULT_RECONNECT_MAX_MS));
    }

    public void stopStream() {
        streamRunning = false;
        streamGeneration.incrementAndGet();
        synchronized (reconnectWaitMonitor) {
            reconnectWaitMonitor.notifyAll();
        }

        BufferedReader reader = activeReader;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Normal shutdown: closing the stream unblocks readLine().
            }
        }

        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }

        Thread thread = streamThread;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(STOP_JOIN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!thread.isAlive() || thread == Thread.currentThread()) {
                streamThread = null;
            }
        }
    }

    public void streamViolations(Consumer<ViolationRecord> onViolation) {
        stopStream();
        streamRunning = true;
        long generation = streamGeneration.incrementAndGet();
        Thread thread = new Thread(() -> runStreamLoop(generation, onViolation), "Zeus-SSE-Stream");
        streamThread = thread;
        thread.start();
    }

    public void clearViolations(List<String> uids) {
        if (uids == null || uids.isEmpty()) return;
        System.out.println("[ZeusPunishment] Public acknowledgement endpoint unavailable; acknowledgement deferred to workflow phase.");
    }

    public boolean probeCompatibility() {
        try {
            JSONArray ignored = getPublicJsonArray(PUBLIC_LIST_MODELS_PATH);
            return ignored != null;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> fetchActiveModels() {
        List<String> models = new ArrayList<>();
        try {
            JSONArray jsonArray = getPublicJsonArray(PUBLIC_LIST_MODELS_PATH);
            if (jsonArray == null) return models;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                if (obj.has("id")) {
                    models.add(obj.getString("id"));
                }
            }
        } catch (Exception e) {
            System.err.println("[ZeusPunishment] Failed to fetch configured detection profiles: " + e.getMessage());
        }
        return models;
    }

    private void runStreamLoop(long generation, Consumer<ViolationRecord> onViolation) {
        int reconnectDelayMs = reconnectInitialMs;
        while (isCurrentGeneration(generation)) {
            try {
                if (!connectAndReadStream(generation, onViolation)) {
                    reconnectDelayMs = nextDelay(reconnectDelayMs);
                } else {
                    reconnectDelayMs = reconnectInitialMs;
                }
            } catch (Exception e) {
                if (isCurrentGeneration(generation)) {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    System.err.println("[ZeusPunishment] Public violation stream closed: " + msg + ". Reconnecting with backoff.");
                    reconnectDelayMs = nextDelay(reconnectDelayMs);
                }
            } finally {
                clearActiveResources();
            }

            if (!waitForReconnect(generation, reconnectDelayMs)) {
                break;
            }
        }
        clearActiveResources();
    }

    private boolean connectAndReadStream(long generation, Consumer<ViolationRecord> onViolation) throws Exception {
        if (!isCurrentGeneration(generation)) return false;
        URL url = new URL(baseUrl + PUBLIC_VIOLATIONS_STREAM_PATH);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        activeConnection = conn;
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(0);

        if (!isCurrentGeneration(generation)) return false;
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            if (isCurrentGeneration(generation)) {
                System.err.println("[ZeusPunishment] Public violation stream returned HTTP code: " + responseCode);
            }
            return false;
        }

        System.out.println("[ZeusPunishment] Connected to public violation stream.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            activeReader = reader;
            String line;
            while (isCurrentGeneration(generation) && (line = reader.readLine()) != null) {
                if (line.startsWith("data: ") && isCurrentGeneration(generation)) {
                    parseAndDispatch(line.substring(6).trim(), onViolation);
                }
            }
        }
        return true;
    }

    private void parseAndDispatch(String jsonStr, Consumer<ViolationRecord> onViolation) {
        if (jsonStr.isEmpty()) return;
        try {
            ViolationRecord record = ViolationRecord.fromJson(new JSONObject(jsonStr));
            onViolation.accept(record);
        } catch (Exception e) {
            System.err.println("[ZeusPunishment] Error parsing public violation payload: " + e.getMessage());
        }
    }

    private boolean waitForReconnect(long generation, int delayMs) {
        int jitter = Math.max(1, delayMs / 5);
        long waitMs = Math.min(reconnectMaxMs, delayMs + ThreadLocalRandom.current().nextInt(jitter + 1));
        long deadline = System.currentTimeMillis() + waitMs;
        synchronized (reconnectWaitMonitor) {
            while (isCurrentGeneration(generation)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return true;
                try {
                    reconnectWaitMonitor.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private int nextDelay(int currentDelayMs) {
        long next = Math.max(reconnectInitialMs, (long) currentDelayMs * 2L);
        return (int) Math.min(reconnectMaxMs, next);
    }

    private boolean isCurrentGeneration(long generation) {
        return streamRunning && streamGeneration.get() == generation;
    }

    private void clearActiveResources() {
        BufferedReader reader = activeReader;
        activeReader = null;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
        HttpURLConnection connection = activeConnection;
        activeConnection = null;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private JSONArray getPublicJsonArray(String path) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);

        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return null;
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            return new JSONArray(response.toString());
        } finally {
            conn.disconnect();
        }
    }

    private int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
