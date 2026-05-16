package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewCategory;
import com.forestry.aireviewer.dto.AIReviewFinding;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.AIReviewSeverity;
import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.model.DocumentChunk;
import com.forestry.aireviewer.model.DocumentStatus;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.repository.DocumentChunkRepository;
import com.forestry.aireviewer.repository.FindingRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewOrchestratorTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    private DocumentChunker chunker;
    private ReviewService reviewService;
    private AIReviewMapper aiReviewMapper;
    private ReviewOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        chunker = new DocumentChunker();
        reviewService = new MockReviewService();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        aiReviewMapper = new AIReviewMapper(validator);
        orchestrator = new ReviewOrchestrator(
                documentService, findingRepository, chunkRepository, chunker, reviewService, aiReviewMapper);
    }

    @Test
    @DisplayName("reviewDocument iterates over each chunk and persists findings")
    void reviewDocument_iteratesChunksAndPersists() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "extracted body");
        List<DocumentChunk> chunks = List.of(
                chunk(documentId, 0, "chunk zero content"),
                chunk(documentId, 1, "chunk one content"),
                chunk(documentId, 2, "chunk two content"));

        when(documentService.getById(documentId)).thenReturn(doc);
        when(findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(chunks);
        when(findingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Finding> result = orchestrator.reviewDocument(documentId);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(f -> {
            assertThat(f.getDocumentId()).isEqualTo(documentId);
            assertThat(f.getChunkIndex()).isNotNull();
            assertThat(f.getChunkIndex()).isBetween(0, 2);
        });
        verify(findingRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("reviewDocument deduplicates findings produced by multiple chunks")
    void reviewDocument_dedupesAcrossChunks() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "body");
        List<DocumentChunk> chunks = List.of(
                chunk(documentId, 0, "a"),
                chunk(documentId, 1, "b"));

        AIReviewFinding dupA = finding(0.5);
        AIReviewFinding dupB = finding(0.9);

        ReviewService duplicatingProvider = req -> {
            if (req.chunkIndex() == 0) return new AIReviewResponse(List.of(dupA));
            return new AIReviewResponse(List.of(dupB));
        };
        orchestrator = new ReviewOrchestrator(
                documentService, findingRepository, chunkRepository, chunker, duplicatingProvider, aiReviewMapper);

        when(documentService.getById(documentId)).thenReturn(doc);
        when(findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(chunks);
        when(findingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Finding> result = orchestrator.reviewDocument(documentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("reviewDocument falls back to on-demand chunking when no chunks exist")
    void reviewDocument_noChunks_chunksOnDemand() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "Some forestry report body text.");

        when(documentService.getById(documentId)).thenReturn(doc);
        when(findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(List.of());
        when(chunkRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Finding> result = orchestrator.reviewDocument(documentId);

        assertThat(result).isNotEmpty();
        verify(chunkRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("reviewDocument returns cached findings without re-invoking the provider")
    void reviewDocument_returnsCachedFindings() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "content");
        Finding cached = new Finding();
        cached.setDocumentId(documentId);

        when(documentService.getById(documentId)).thenReturn(doc);
        when(findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)).thenReturn(List.of(cached));

        List<Finding> result = orchestrator.reviewDocument(documentId);

        assertThat(result).containsExactly(cached);
        verify(chunkRepository, never()).findByDocumentIdOrderByChunkIndexAsc(any());
        verify(findingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("reviewDocument rejects documents with no extracted text")
    void reviewDocument_rejectsEmptyText() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "");

        when(documentService.getById(documentId)).thenReturn(doc);

        assertThatThrownBy(() -> orchestrator.reviewDocument(documentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no extracted text");

        verify(findingRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("reviewDocument persists nothing when any chunk returns an invalid finding")
    void reviewDocument_invalidChunkResponse_persistsNothing() {
        UUID documentId = UUID.randomUUID();
        Document doc = sampleDocument(documentId, "content");
        List<DocumentChunk> chunks = List.of(
                chunk(documentId, 0, "a"),
                chunk(documentId, 1, "b"));

        ReviewService bogusProvider = req -> {
            if (req.chunkIndex() == 1) {
                return new AIReviewResponse(List.of(
                        new AIReviewFinding(
                                AIReviewCategory.MISSING_EVIDENCE,
                                AIReviewSeverity.HIGH,
                                0.5,
                                "Section 1",
                                "  ",
                                "evidence text",
                                "recommendation text",
                                null)));
            }
            return new AIReviewResponse(List.of(finding(0.4)));
        };
        orchestrator = new ReviewOrchestrator(
                documentService, findingRepository, chunkRepository, chunker, bogusProvider, aiReviewMapper);

        when(documentService.getById(documentId)).thenReturn(doc);
        when(findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId)).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)).thenReturn(chunks);

        assertThatThrownBy(() -> orchestrator.reviewDocument(documentId))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("finding is required");

        verify(findingRepository, never()).saveAll(any());
    }

    private Document sampleDocument(UUID id, String text) {
        Document doc = new Document();
        doc.setId(id);
        doc.setFileName("report.pdf");
        doc.setContentType("application/pdf");
        doc.setOriginalFilePath("/tmp/report.pdf");
        doc.setExtractedText(text);
        doc.setStatus(DocumentStatus.READY);
        doc.setUploadedAt(Instant.now());
        return doc;
    }

    private DocumentChunk chunk(UUID documentId, int index, String content) {
        DocumentChunk c = new DocumentChunk(documentId, index, content, 0, content.length());
        c.setId(UUID.randomUUID());
        return c;
    }

    private AIReviewFinding finding(double confidence) {
        return new AIReviewFinding(
                AIReviewCategory.INTERNAL_CONTRADICTION,
                AIReviewSeverity.HIGH,
                confidence,
                "Section 2.1",
                "Same description across chunks",
                "evidence text",
                "recommendation text",
                null);
    }
}
