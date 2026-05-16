package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewFinding;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingSeverity;
import com.forestry.aireviewer.model.FindingType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates an {@link AIReviewResponse} and converts each
 * {@link AIReviewFinding} into a persistable {@link Finding} entity.
 *
 * <p>Validation runs against the whole response before any conversion; if any
 * finding fails, {@link InvalidAIReviewException} is thrown and no entities
 * are returned. This guarantees the orchestrator never persists a partial
 * batch from a malformed AI response.</p>
 */
@Component
public class AIReviewMapper {

    private final Validator validator;

    public AIReviewMapper(Validator validator) {
        this.validator = validator;
    }

    public List<Finding> toEntities(AIReviewResponse response, UUID documentId, Integer chunkIndex) {
        if (response == null) {
            throw new InvalidAIReviewException("AI review response is null");
        }
        validate(response);

        List<Finding> entities = new ArrayList<>(response.findings().size());
        for (AIReviewFinding dto : response.findings()) {
            entities.add(toEntity(dto, documentId, chunkIndex));
        }
        return entities;
    }

    public List<Finding> toEntities(AIReviewResponse response, UUID documentId) {
        return toEntities(response, documentId, null);
    }

    private void validate(AIReviewResponse response) {
        Set<ConstraintViolation<AIReviewResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new InvalidAIReviewException(
                    "AI review response failed validation: " + detail);
        }
    }

    private Finding toEntity(AIReviewFinding dto, UUID documentId, Integer chunkIndex) {
        Finding f = new Finding();
        f.setDocumentId(documentId);
        f.setChunkIndex(chunkIndex);
        f.setType(FindingType.valueOf(dto.category().name()));
        f.setSeverity(FindingSeverity.valueOf(dto.severity().name()));
        f.setLocation(dto.section());
        f.setDescription(dto.finding());
        f.setSuggestion(dto.recommendation());
        f.setEvidence(dto.evidence());
        f.setConfidence(dto.confidence());
        if (dto.sourceReferences() != null && !dto.sourceReferences().isEmpty()) {
            f.setSourceReferences(String.join("\n", dto.sourceReferences()));
        }
        return f;
    }
}
