package com.forestry.aireviewer.service;

import com.forestry.aireviewer.client.AiReviewClient;
import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.repository.FindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final DocumentService documentService;
    private final FindingRepository findingRepository;
    private final AiReviewClient aiReviewClient;

    public ReviewService(DocumentService documentService,
                         FindingRepository findingRepository,
                         AiReviewClient aiReviewClient) {
        this.documentService = documentService;
        this.findingRepository = findingRepository;
        this.aiReviewClient = aiReviewClient;
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

        List<Finding> findings = aiReviewClient.review(
                doc.getExtractedText(), doc.getFileName(), documentId);

        findings = findingRepository.saveAll(findings);
        log.info("Generated {} findings for document '{}'", findings.size(), doc.getFileName());
        return findings;
    }

    public List<Finding> getFindings(UUID documentId) {
        return findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
    }

    public Finding updateStatus(UUID findingId, FindingStatus status) {
        Finding finding = findingRepository.findById(findingId)
                .orElseThrow(() -> new RuntimeException("Finding not found: " + findingId));
        finding.setStatus(status);
        return findingRepository.save(finding);
    }
}
