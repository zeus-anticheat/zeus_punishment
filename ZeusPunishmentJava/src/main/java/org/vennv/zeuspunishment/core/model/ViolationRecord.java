package org.vennv.zeuspunishment.core.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ViolationRecord {
    private final String uid;
    private final String username;
    private final int warningCount;
    private final float totalPoints;
    private final Severity severity;
    private final List<ViolationLog> logs;

    public ViolationRecord(String uid, String username, int warningCount, float totalPoints, Severity severity, List<ViolationLog> logs) {
        this.uid = uid;
        this.username = username;
        this.warningCount = warningCount;
        this.totalPoints = totalPoints;
        this.severity = severity;
        this.logs = logs;
    }

    public static ViolationRecord fromJson(JSONObject json) {
        JSONArray logsArray = json.optJSONArray("logs");
        List<ViolationLog> parsedLogs = new ArrayList<>();
        if (logsArray != null) {
            for (int i = 0; i < logsArray.length(); i++) {
                parsedLogs.add(ViolationLog.fromJson(logsArray.getJSONObject(i)));
            }
        }

        return new ViolationRecord(
                json.getString("uid"),
                json.getString("username"),
                json.optInt("warning_count", 0),
                (float) json.optDouble("total_points", 0.0),
                Severity.fromString(json.optString("severity", "Normal")),
                parsedLogs
        );
    }

    public String getUid() { return uid; }
    public String getUsername() { return username; }
    public int getWarningCount() { return warningCount; }
    public float getTotalPoints() { return totalPoints; }
    public Severity getSeverity() { return severity; }
    public List<ViolationLog> getLogs() { return logs; }
}
