package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewCategory;
import com.forestry.aireviewer.dto.AIReviewFinding;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.AIReviewSeverity;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingSeverity;
import com.forestry.aireviewer.model.FindingType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIReviewMapperTest {

    private AIReviewMapper mapper;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        mapper = new AIReviewMapper(validator);
    }

    @Test
    @DisplayName("toEntities converts a valid response into Finding entities tagged with the chunk index")
    void toEntities_validResponse_converts() {
        UUID documentId = UUID.randomUUID();
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.ENVIRONMENTAL_IMPACT,
                        AIReviewSeverity.HIGH,
                        0.85,
                        "Section 4",
                        "Project lacks impact assessment for downstream watershed.",
                        "No watershed analysis is included in the assessment chapter.",
                        "Add a watershed impact section with quantitative analysis.",
                        List.of("Watershed Protection Act §3", "EIA Guideline 12.4"))
        ));

        List<Finding> entities = mapper.toEntities(response, documentId, 3);

        assertThat(entities).hasSize(1);
        Finding f = entities.get(0);
        assertThat(f.getDocumentId()).isEqualTo(documentId);
        assertThat(f.getChunkIndex()).isEqualTo(3);
        assertThat(f.getType()).isEqualTo(FindingType.ENVIRONMENTAL_IMPACT);
        assertThat(f.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(f.getConfidence()).isEqualTo(0.85);
        assertThat(f.getLocation()).isEqualTo("Section 4");
        assertThat(f.getDescription()).isEqualTo("Project lacks impact assessment for downstream watershed.");
        assertThat(f.getEvidence()).isEqualTo("No watershed analysis is included in the assessment chapter.");
        assertThat(f.getSuggestion()).isEqualTo("Add a watershed impact section with quantitative analysis.");
        assertThat(f.getSourceReferences()).isEqualTo("Watershed Protection Act §3\nEIA Guideline 12.4");
    }

    @Test
    @DisplayName("toEntities defaults chunkIndex to null when not provided")
    void toEntities_noChunkIndex_leavesNull() {
        AIReviewResponse response = new AIReviewResponse(List.of(validFinding()));

        List<Finding> entities = mapper.toEntities(response, UUID.randomUUID());

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getChunkIndex()).isNull();
    }

    @Test
    @DisplayName("toEntities rejects a null response")
    void toEntities_nullResponse_throws() {
        assertThatThrownBy(() -> mapper.toEntities(null, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("toEntities rejects a response with no findings list")
    void toEntities_nullFindings_throws() {
        AIReviewResponse response = new AIReviewResponse(null);

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("findings");
    }

    @Test
    @DisplayName("toEntities rejects findings with blank required fields")
    void toEntities_blankRequiredField_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        0.5,
                        "Section 1",
                        "",
                        "evidence",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("finding is required");
    }

    @Test
    @DisplayName("toEntities rejects findings with blank evidence")
    void toEntities_blankEvidence_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        0.5,
                        "Section 1",
                        "finding text",
                        "   ",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("evidence is required");
    }

    @Test
    @DisplayName("toEntities rejects findings with blank recommendation")
    void toEntities_blankRecommendation_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        0.5,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("recommendation is required");
    }

    @Test
    @DisplayName("toEntities rejects findings with null category")
    void toEntities_nullCategory_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        null,
                        AIReviewSeverity.MEDIUM,
                        0.5,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("category is required");
    }

    @Test
    @DisplayName("toEntities rejects findings with null severity")
    void toEntities_nullSeverity_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        null,
                        0.5,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("severity is required");
    }

    @Test
    @DisplayName("toEntities rejects findings with confidence above 1.0")
    void toEntities_confidenceTooHigh_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        1.5,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("toEntities rejects findings with negative confidence")
    void toEntities_confidenceNegative_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        -0.1,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("toEntities rejects findings with null confidence")
    void toEntities_nullConfidence_throws() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        null,
                        "Section 1",
                        "finding text",
                        "evidence text",
                        "recommendation",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("confidence is required");
    }

    @Test
    @DisplayName("toEntities throws on the first invalid finding before persisting anything")
    void toEntities_oneInvalidInBatch_throwsAndReturnsNothing() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                validFinding(),
                new AIReviewFinding(
                        AIReviewCategory.MISSING_EVIDENCE,
                        AIReviewSeverity.MEDIUM,
                        0.7,
                        "Section 2",
                        "another finding",
                        "",
                        "rec",
                        null)));

        assertThatThrownBy(() -> mapper.toEntities(response, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("evidence is required");
    }

    @Test
    @DisplayName("toEntities accepts an empty findings list")
    void toEntities_emptyFindings_returnsEmpty() {
        assertThat(mapper.toEntities(new AIReviewResponse(List.of()), UUID.randomUUID(), 0)).isEmpty();
    }

    @Test
    @DisplayName("toEntities accepts confidence at the boundary values 0.0 and 1.0")
    void toEntities_confidenceBoundaries_accepted() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.WEAK_MITIGATION,
                        AIReviewSeverity.LOW,
                        0.0,
                        "Section A",
                        "finding",
                        "evidence",
                        "recommendation",
                        null),
                new AIReviewFinding(
                        AIReviewCategory.POSSIBLE_REGULATORY_CONCERN,
                        AIReviewSeverity.HIGH,
                        1.0,
                        "Section B",
                        "finding",
                        "evidence",
                        "recommendation",
                        null)));

        List<Finding> entities = mapper.toEntities(response, UUID.randomUUID(), 0);

        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).getConfidence()).isEqualTo(0.0);
        assertThat(entities.get(1).getConfidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("toEntities leaves sourceReferences null when DTO list is null or empty")
    void toEntities_emptySourceReferences_leavesNull() {
        AIReviewResponse response = new AIReviewResponse(List.of(
                new AIReviewFinding(
                        AIReviewCategory.VAGUE_LANGUAGE,
                        AIReviewSeverity.LOW,
                        0.4,
                        "Section X",
                        "finding",
                        "evidence",
                        "recommendation",
                        null),
                new AIReviewFinding(
                        AIReviewCategory.VAGUE_LANGUAGE,
                        AIReviewSeverity.LOW,
                        0.4,
                        "Section Y",
                        "finding",
                        "evidence",
                        "recommendation",
                        List.of())));

        List<Finding> entities = mapper.toEntities(response, UUID.randomUUID(), 0);

        assertThat(entities.get(0).getSourceReferences()).isNull();
        assertThat(entities.get(1).getSourceReferences()).isNull();
    }

    private AIReviewFinding validFinding() {
        return new AIReviewFinding(
                AIReviewCategory.MISSING_EVIDENCE,
                AIReviewSeverity.HIGH,
                0.7,
                "Section 1",
                "finding text",
                "evidence text",
                "recommendation",
                null);
    }
}
