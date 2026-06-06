package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.BulkImportFinding;
import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.model.DocumentChunk;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingSeverity;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.model.FindingType;
import com.forestry.aireviewer.repository.DocumentChunkRepository;
import com.forestry.aireviewer.repository.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates a document review: loads document chunks, runs the configured
 * {@link ReviewService} provider per chunk, validates each response, dedupes
 * cross-chunk duplicates, and persists the surviving findings.
 *
 * <p>If a chunk's response fails validation the orchestrator throws
 * {@link InvalidAIReviewException} and persists nothing — partial batches
 * never reach the database.</p>
 */
@Service
public class ReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final DocumentService documentService;
    private final FindingRepository findingRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunker chunker;
    private final ReviewService reviewService;
    private final AIReviewMapper aiReviewMapper;

    public ReviewOrchestrator(DocumentService documentService,
                              FindingRepository findingRepository,
                              DocumentChunkRepository chunkRepository,
                              DocumentChunker chunker,
                              ReviewService reviewService,
                              AIReviewMapper aiReviewMapper) {
        this.documentService = documentService;
        this.findingRepository = findingRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.reviewService = reviewService;
        this.aiReviewMapper = aiReviewMapper;
        log.info("ReviewOrchestrator initialised with provider: {}", reviewService.getClass().getSimpleName());
    }

    public List<Finding> reviewDocument(UUID documentId) {
        Document doc = documentService.getById(documentId);

        if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
            throw new RuntimeException("Document has no extracted text: " + documentId);
        }

        List<Finding> existing = findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        if (!existing.isEmpty()) {
            log.info("Document {} already has {} findings, returning existing", documentId, existing.size());
            return existing;
        }

        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            chunks = chunkOnDemand(doc);
        }
        if (chunks.isEmpty()) {
            throw new RuntimeException("Document " + documentId + " produced no chunks");
        }

        List<Finding> accumulated = new ArrayList<>();
        int total = chunks.size();
        for (DocumentChunk chunk : chunks) {
            ReviewRequest request = new ReviewRequest(
                    documentId,
                    doc.getFileName(),
                    chunk.getContent(),
                    chunk.getChunkIndex(),
                    total);

            AIReviewResponse response = reviewService.review(request);
            List<Finding> chunkFindings = aiReviewMapper.toEntities(response, documentId, chunk.getChunkIndex());
            accumulated.addAll(chunkFindings);
        }

        List<Finding> deduped = dedupe(accumulated);
        List<Finding> saved = findingRepository.saveAll(deduped);
        log.info("Generated {} findings ({} after dedup) across {} chunks for document '{}'",
                accumulated.size(), saved.size(), total, doc.getFileName());
        return saved;
    }

    public List<Finding> getFindings(UUID documentId) {
        return findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    public List<Finding> bulkImport(UUID documentId, List<BulkImportFinding> requests) {
        Document doc = documentService.getById(documentId);
        List<Finding> findings = new ArrayList<>(requests.size());
        for (BulkImportFinding r : requests) {
            Finding f = new Finding();
            f.setDocumentId(doc.getId());
            f.setType(parseType(r.type()));
            f.setSeverity(parseSeverity(r.severity()));
            f.setLocation(r.location());
            f.setQuote(r.quote());
            f.setDescription(r.description());
            f.setSuggestion(r.suggestion());
            f.setEvidence(r.evidence());
            f.setConfidence(r.confidence());
            f.setSourceReferences(r.sourceReferences());
            f.setChunkIndex(r.chunkIndex());
            f.setStatus(FindingStatus.PENDING);
            findings.add(f);
        }
        List<Finding> saved = findingRepository.saveAll(findings);
        log.info("Bulk-imported {} findings for document '{}'", saved.size(), doc.getFileName());
        return saved;
    }

    private static FindingType parseType(String value) {
        try {
            return FindingType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown finding type: " + value, e);
        }
    }

    private static FindingSeverity parseSeverity(String value) {
        try {
            return FindingSeverity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown severity: " + value, e);
        }
    }

    public Finding updateStatus(UUID findingId, FindingStatus status) {
        Finding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new RuntimeException("Finding not found: " + findingId));
        finding.setStatus(status);
        return findingRepository.save(finding);
    }

    private List<DocumentChunk> chunkOnDemand(Document doc) {
        log.info("No chunks found for document {} — chunking on demand", doc.getId());
        List<DocumentChunker.ChunkData> data = chunker.chunk(doc.getExtractedText());
        List<DocumentChunk> persisted = new ArrayList<>(data.size());
        for (DocumentChunker.ChunkData d : data) {
            persisted.add(new DocumentChunk(doc.getId(), d.chunkIndex(), d.content(), d.startOffset(), d.endOffset()));
        }
        return chunkRepository.saveAll(persisted);
    }

    /**
     * Collapse duplicates produced across chunks. Findings with the same
     * {@code (location, description)} pair are merged; the one with the
     * highest confidence wins.
     */
    private List<Finding> dedupe(List<Finding> findings) {
        Map<String, Finding> bestByKey = new LinkedHashMap<>();
        for (Finding f : findings) {
            String key = key(f);
            Finding existing = bestByKey.get(key);
            if (existing == null || confidenceOf(f) > confidenceOf(existing)) {
                bestByKey.put(key, f);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }

    private static String key(Finding f) {
        String location = f.getLocation() == null ? "" : f.getLocation();
        String description = f.getDescription() == null ? "" : f.getDescription();
        return location + "||" + description;
    }

    private static double confidenceOf(Finding f) {
        return f.getConfidence() == null ? 0.0 : f.getConfidence();
    }
}
