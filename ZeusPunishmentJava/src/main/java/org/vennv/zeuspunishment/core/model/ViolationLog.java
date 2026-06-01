package org.vennv.zeuspunishment.core.model;

import org.json.JSONObject;

public class ViolationLog {
    private final long timestamp;
    private final String modelId;
    private final String modelKind;
    private final int ping;
    private final float violationScore;
    private final String explanation;

    public ViolationLog(long timestamp, String modelId, String modelKind, int ping, float violationScore, String explanation) {
        this.timestamp = timestamp;
        this.modelId = modelId;
        this.modelKind = modelKind;
        this.ping = ping;
        this.violationScore = violationScore;
        this.explanation = explanation;
    }

    public static ViolationLog fromJson(JSONObject json) {
        return new ViolationLog(
                json.optLong("timestamp"),
                json.optString("model_id"),
                json.optString("model_kind"),
                json.optInt("ping"),
                (float) json.optDouble("violation_score", 0.0),
                json.has("explanation") && !json.isNull("explanation") ? json.getString("explanation") : null
        );
    }

    public long getTimestamp() { return timestamp; }
    public String getModelId() { return modelId; }
    public String getModelKind() { return modelKind; }
    public int getPing() { return ping; }
    public float getViolationScore() { return violationScore; }
    public String getExplanation() { return explanation; }
}
