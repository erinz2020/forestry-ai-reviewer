package com.forestry.aireviewer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AIReviewSeverity {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String json;

    AIReviewSeverity(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static AIReviewSeverity fromJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("severity is required");
        }
        for (AIReviewSeverity s : values()) {
            if (s.json.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown AI review severity: " + value);
    }
}
