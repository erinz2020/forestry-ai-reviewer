package com.forestry.aireviewer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BulkImportFinding(
        @NotNull String type,
        @NotNull String severity,
        String location,
        String quote,
        @NotBlank String description,
        String suggestion,
        String evidence,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        String sourceReferences,
        Integer chunkIndex
) {}
