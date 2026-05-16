package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewFinding;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.AIReviewSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MockReviewServiceTest {

    private final MockReviewService mockReviewService = new MockReviewService();

    @Test
    @DisplayName("review returns a non-empty response with valid findings for a single chunk")
    void review_singleChunk_returnsValidResponse() {
        AIReviewResponse response = mockReviewService.review(new ReviewRequest(
                UUID.randomUUID(),
                "feasibility-report.pdf",
                "Sample chunk content.",
                0,
                1));

        assertThat(response).isNotNull();
        assertThat(response.findings()).isNotEmpty();
        assertThat(response.findings()).allSatisfy(finding -> {
            assertThat(finding.category()).isNotNull();
            assertThat(finding.severity()).isNotNull();
            assertThat(finding.confidence()).isBetween(0.0, 1.0);
            assertThat(finding.finding()).isNotBlank();
            assertThat(finding.evidence()).isNotBlank();
            assertThat(finding.recommendation()).isNotBlank();
        });
    }

    @Test
    @DisplayName("review yields different finding mixes across chunks (chunk-aware)")
    void review_multiChunk_variesByChunkIndex() {
        UUID documentId = UUID.randomUUID();

        List<AIReviewFinding> chunk0 = mockReviewService.review(
                new ReviewRequest(documentId, "doc.pdf", "chunk 0", 0, 5)).findings();
        List<AIReviewFinding> chunk1 = mockReviewService.review(
                new ReviewRequest(documentId, "doc.pdf", "chunk 1", 1, 5)).findings();

        assertThat(chunk0).isNotEmpty();
        assertThat(chunk1).isNotEmpty();
        assertThat(chunk0)
                .extracting(AIReviewFinding::category)
                .isNotEqualTo(chunk1.stream().map(AIReviewFinding::category).toList());
    }

    @Test
    @DisplayName("over a typical multi-chunk run the mock surfaces every severity level")
    void review_multipleChunks_coversAllSeverities() {
        UUID documentId = UUID.randomUUID();
        List<AIReviewFinding> all = IntStream.range(0, 7)
                .mapToObj(i -> mockReviewService.review(
                        new ReviewRequest(documentId, "doc.pdf", "body", i, 7)))
                .flatMap(r -> r.findings().stream())
                .toList();

        assertThat(all)
                .extracting(AIReviewFinding::severity)
                .contains(AIReviewSeverity.HIGH, AIReviewSeverity.MEDIUM, AIReviewSeverity.LOW);
    }

    @Test
    @DisplayName("review is deterministic for repeated calls with the same chunk index")
    void review_isDeterministic() {
        UUID documentId = UUID.randomUUID();

        AIReviewResponse first = mockReviewService.review(
                new ReviewRequest(documentId, "doc.pdf", "body", 2, 5));
        AIReviewResponse second = mockReviewService.review(
                new ReviewRequest(documentId, "doc.pdf", "body", 2, 5));

        assertThat(second.findings()).hasSameSizeAs(first.findings());
        for (int i = 0; i < first.findings().size(); i++) {
            assertThat(second.findings().get(i).category())
                    .isEqualTo(first.findings().get(i).category());
            assertThat(second.findings().get(i).section())
                    .isEqualTo(first.findings().get(i).section());
        }
    }
}
