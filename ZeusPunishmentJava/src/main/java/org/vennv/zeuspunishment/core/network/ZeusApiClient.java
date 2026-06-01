package org.vennv.zeuspunishment.core.network;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

public class ZeusApiClient {
    public static final String PUBLIC_VIOLATIONS_PATH = "/api/public/violations";
    public static final String PUBLIC_VIOLATIONS_STREAM_PATH = "/api/public/violations/stream";
    public static final String PUBLIC_LIST_MODELS_PATH = "/api/public/list_models";

    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int reconnectMaxMs;
    private final int reconnectInitialMs;
    private Thread streamThread;
    private volatile HttpURLConnection activeConnection;
    private volatile Closeable activeReader;
    private final AtomicLong streamGeneration = new AtomicLong();
    private volatile boolean apiHealthy = false;
    private volatile boolean streamRunning = false;
    private volatile boolean reconnecting = false;
    private volatile int currentBackoffMs = 0;
    private volatile String lastOperationalError = "none";

    public ZeusApiClient(String baseUrl) {
        this(baseUrl, 5000, 0, 5000, 5000);
    }

    public ZeusApiClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs, int reconnectMaxMs, int reconnectInitialMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.reconnectMaxMs = reconnectMaxMs;
        this.reconnectInitialMs = reconnectInitialMs;
    }

    public void stopStream() {
        HttpURLConnection conn = activeConnection;
        if (conn != null) conn.disconnect();
        Closeable reader = activeReader;
        if (reader != null) {
            try { reader.close(); } catch (Exception ignored) { }
        }
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
        }
        streamThread = null;
        activeConnection = null;
        activeReader = null;
        streamRunning = false;
        reconnecting = false;
    }

    public void streamViolations(Consumer<ViolationRecord> onViolation) {
        stopStream();
        long generation = streamGeneration.incrementAndGet();
        streamThread = new Thread(() -> {
            while (true) {
                try {
                    URL url = new URL(baseUrl + PUBLIC_VIOLATIONS_STREAM_PATH);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(connectTimeoutMs);
                    conn.setReadTimeout(readTimeoutMs);
                    activeConnection = conn;

                    if (conn.getResponseCode() == 200) {
                        apiHealthy = true;
                        streamRunning = true;
                        reconnecting = false;
                        currentBackoffMs = 0;
                        lastOperationalError = "none";
                        System.out.println("[ZeusPunishment] Connected to SSE Real-Time Violation Stream!");
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        activeReader = in;
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String jsonStr = line.substring(6).trim();
                                if (!jsonStr.isEmpty()) {
                                    try {
                                        ViolationRecord record = ViolationRecord.fromJson(new JSONObject(jsonStr));
                                        onViolation.accept(record);
                                    } catch (Exception e) {
                                        lastOperationalError = "stream payload parse error";
                                        System.err.println("[ZeusPunishment] Error parsing SSE payload: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        in.close();
                        activeReader = null;
                    } else {
                        apiHealthy = false;
                        streamRunning = false;
                        reconnecting = true;
                        currentBackoffMs = reconnectInitialMs;
                        lastOperationalError = "stream returned status " + conn.getResponseCode();
                        System.err.println("[ZeusPunishment] SSE Stream returned non-200 HTTP code: " + conn.getResponseCode());
                    }
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null) msg = e.getClass().getSimpleName();
                    apiHealthy = false;
                    streamRunning = false;
                    reconnecting = true;
                    currentBackoffMs = 5000;
                    lastOperationalError = "stream connection closed";
                    System.err.println("[ZeusPunishment] SSE connection closed: " + msg + ". Reconnecting in 5s...");
                }

                try {
                    Thread.sleep(Math.min(reconnectMaxMs, reconnectInitialMs));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Zeus-SSE-Stream-" + generation);
        streamThread.start();
    }

    public boolean clearViolations(List<String> uids) {
        if (uids == null || uids.isEmpty()) return true;
        try {
            URL url = new URL(baseUrl + PUBLIC_VIOLATIONS_PATH);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            JSONObject payload = new JSONObject();
            JSONArray uidsArray = new JSONArray();
            for (String uid : uids) uidsArray.put(uid);
            payload.put("uids", uidsArray);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("[ZeusPunishment] Failed to clear violations, HTTP code: " + responseCode);
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ZeusPunishment] Error clearing violations: " + e.getMessage());
            return false;
        }
    }
    public boolean probeCompatibility() {
        try {
            URL url = new URL(baseUrl + "/api/public/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            boolean ok = conn.getResponseCode() >= 200 && conn.getResponseCode() < 500;
            apiHealthy = ok;
            if (!ok) lastOperationalError = "health check unavailable";
            return ok;
        } catch (Exception e) {
            apiHealthy = false;
            lastOperationalError = "health check unavailable";
            return false;
        }
    }

    public boolean acknowledgeViolations(List<String> uids) {
        if (uids == null || uids.isEmpty()) return true;
        return clearViolations(uids);
    }

    public ApiStatusSnapshot getStatusSnapshot() {
        return new ApiStatusSnapshot(apiHealthy, streamRunning, reconnecting, streamGeneration.get(), currentBackoffMs, lastOperationalError);
    }

    public List<String> fetchActiveModels() {
        List<String> models = new ArrayList<>();
        try {
            URL url = new URL(baseUrl + PUBLIC_LIST_MODELS_PATH);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.has("id")) {
                        models.add(obj.getString("id"));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ZeusPunishment] Failed to fetch active models: " + e.getMessage());
        }
        return models;
    }
}
