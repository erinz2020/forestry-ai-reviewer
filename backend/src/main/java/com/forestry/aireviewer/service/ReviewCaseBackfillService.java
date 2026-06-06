package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.DocumentType;
import com.forestry.aireviewer.model.ReviewCase;
import com.forestry.aireviewer.repository.DocumentTypeRepository;
import com.forestry.aireviewer.repository.ReviewCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One-shot backfill that walks review_cases without a documentTypeId and
 * tries to assign one based on the filename. Idempotent — re-running only
 * touches rows that still have NULL documentTypeId.
 */
@Service
public class ReviewCaseBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ReviewCaseBackfillService.class);

    private final ReviewCaseRepository reviewCaseRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentTypeClassifier classifier;

    public ReviewCaseBackfillService(ReviewCaseRepository reviewCaseRepository,
                                     DocumentTypeRepository documentTypeRepository,
                                     DocumentTypeClassifier classifier) {
        this.reviewCaseRepository = reviewCaseRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.classifier = classifier;
    }

    public record BackfillResult(int scanned, int assigned, int unmatched, Map<String, Integer> perCode) {}

    @Transactional
    public BackfillResult backfillUnassigned() {
        Map<String, UUID> codeToId = new HashMap<>();
        for (DocumentType t : documentTypeRepository.findAll()) {
            codeToId.put(t.getCode(), t.getId());
        }

        List<ReviewCase> pending = reviewCaseRepository.findByDocumentTypeIdIsNull();
        int assigned = 0;
        int unmatched = 0;
        Map<String, Integer> perCode = new TreeMap<>();

        for (ReviewCase rc : pending) {
            Optional<String> code = classifier.classifyByFilename(rc.getSourceReviewedFileName());
            if (code.isPresent()) {
                UUID typeId = codeToId.get(code.get());
                if (typeId == null) {
                    unmatched++;
                    continue;
                }
                rc.setDocumentTypeId(typeId);
                assigned++;
                perCode.merge(code.get(), 1, Integer::sum);
            } else {
                unmatched++;
            }
        }

        reviewCaseRepository.saveAll(pending);
        log.info("Backfill complete: scanned={}, assigned={}, unmatched={}", pending.size(), assigned, unmatched);
        return new BackfillResult(pending.size(), assigned, unmatched, perCode);
    }
}
