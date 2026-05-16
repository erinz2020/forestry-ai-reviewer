package com.forestry.aireviewer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Strict shape of a single AI-generated review finding.
 *
 * <p>{@code category}, {@code severity}, and {@code confidence} are constrained
 * by enums and range checks. {@code finding}, {@code evidence}, and
 * {@code recommendation} are required and may not be blank.</p>
 */
public record AIReviewFinding(
        @NotNull(message = "category is required") AIReviewCategory category,

        @NotNull(message = "severity is required") AIReviewSeverity severity,

        @NotNull(message = "confidence is required")
        @DecimalMin(value = "0.0", message = "confidence must be >= 0.0")
        @DecimalMax(value = "1.0", message = "confidence must be <= 1.0")
        Double confidence,

        String section,

        @NotBlank(message = "finding is required") String finding,

        @NotBlank(message = "evidence is required") String evidence,

        @NotBlank(message = "recommendation is required") String recommendation,

        List<String> sourceReferences
) {
}
