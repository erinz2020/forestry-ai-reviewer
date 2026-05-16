package com.forestry.aireviewer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Top-level response shape returned by a {@code ReviewService} provider.
 * The wrapper exists so that future fields (e.g. token usage, prompt version)
 * can be added without breaking the contract.
 */
public record AIReviewResponse(
        @NotNull(message = "findings list is required")
        @Valid
        List<AIReviewFinding> findings
) {
}
