package org.vennv.zeuspunishment.core.network;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vennv.zeuspunishment.core.model.ViolationRecord;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ZeusApiClient {
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
                    URL url = new URL(baseUrl + "/api/public/violations/stream");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(0); // Infinite read timeout for SSE stream

                    if (conn.getResponseCode() == 200) {
                        System.out.println("[ZeusPunishment] Connected to SSE Real-Time Violation Stream!");
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
                                        System.err.println("[ZeusPunishment] Error parsing SSE payload: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        in.close();
                    } else {
                        System.err.println("[ZeusPunishment] SSE Stream returned non-200 HTTP code: " + conn.getResponseCode());
                    }
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null) msg = e.getClass().getSimpleName();
                    System.err.println("[ZeusPunishment] SSE connection closed: " + msg + ". Reconnecting in 5s...");
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
        try {
            URL url = new URL(baseUrl + "/api/public/violations/delete");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
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
            }
        } catch (Exception e) {
            System.err.println("[ZeusPunishment] Error clearing violations: " + e.getMessage());
        }
    }
    public List<String> fetchActiveModels() {
        List<String> models = new ArrayList<>();
        try {
            URL url = new URL(baseUrl + "/api/public/list_models");
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
