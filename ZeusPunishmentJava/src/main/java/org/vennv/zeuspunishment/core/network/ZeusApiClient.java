package org.vennv.zeuspunishment.core.network;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final String baseUrl;
    private Thread streamThread;

    public ZeusApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void stopStream() {
        if (streamThread != null && streamThread.isAlive()) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    public void streamViolations(Consumer<ViolationRecord> onViolation) {
        stopStream();
        streamThread = new Thread(() -> {
            while (true) {
                try {
                    URL url = new URL(baseUrl + PUBLIC_VIOLATIONS_STREAM_PATH);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(0); // Infinite read timeout for SSE stream

                    if (conn.getResponseCode() == 200) {
                        System.out.println("[ZeusPunishment] Connected to public violation stream.");
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String jsonStr = line.substring(6).trim();
                                if (!jsonStr.isEmpty()) {
                                    try {
                                        ViolationRecord record = ViolationRecord.fromJson(new JSONObject(jsonStr));
                                        onViolation.accept(record);
                                    } catch (Exception e) {
                                        System.err.println("[ZeusPunishment] Error parsing public violation payload: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        in.close();
                    } else {
                        System.err.println("[ZeusPunishment] Public violation stream returned HTTP code: " + conn.getResponseCode());
                    }
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null) msg = e.getClass().getSimpleName();
                    System.err.println("[ZeusPunishment] Public violation stream closed: " + msg + ". Reconnecting in 5s...");
                }

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Zeus-SSE-Stream");
        streamThread.start();
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

    private JSONArray getPublicJsonArray(String path) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);

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
}
