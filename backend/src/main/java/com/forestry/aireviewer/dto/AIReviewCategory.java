package com.forestry.aireviewer.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Allowed categories for AI-generated review findings. The JSON form is
 * lowercase snake_case to match the wire format the LLM is expected to emit.
 */
public enum AIReviewCategory {
    INTERNAL_CONTRADICTION("internal_contradiction"),
    MISSING_EVIDENCE("missing_evidence"),
    BIODIVERSITY_RISK("biodiversity_risk"),
    ENVIRONMENTAL_IMPACT("environmental_impact"),
    UNSUPPORTED_CONCLUSION("unsupported_conclusion"),
    VAGUE_LANGUAGE("vague_language"),
    WEAK_MITIGATION("weak_mitigation"),
    POSSIBLE_REGULATORY_CONCERN("possible_regulatory_concern");

    private final String json;

    AIReviewCategory(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static AIReviewCategory fromJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("category is required");
        }
        for (AIReviewCategory c : values()) {
            if (c.json.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown AI review category: " + value);
    }
}
