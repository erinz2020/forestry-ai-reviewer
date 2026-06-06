package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.ReviewCase;
import com.forestry.aireviewer.model.ReviewCaseSourceType;
import com.forestry.aireviewer.repository.ReviewCaseRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class HistoricalReviewPairService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalReviewPairService.class);
    private static final double MEANINGFUL_CHANGE_THRESHOLD = 0.03;
    private static final int PREVIEW_LIMIT = 700;

    private final ReviewCaseRepository reviewCaseRepository;
    private final DocumentChunker chunker;
    private final CommentExtractionDispatcher commentExtractor;
    private final DocxRevisionExtractor revisionExtractor;
    private final Tika tika = new Tika();

    public HistoricalReviewPairService(ReviewCaseRepository reviewCaseRepository,
                                       DocumentChunker chunker,
                                       CommentExtractionDispatcher commentExtractor,
                                       DocxRevisionExtractor revisionExtractor) {
        this.reviewCaseRepository = reviewCaseRepository;
        this.chunker = chunker;
        this.commentExtractor = commentExtractor;
        this.revisionExtractor = revisionExtractor;
    }

    public List<ReviewCase> ingestPair(MultipartFile beforeFile,
                                       MultipartFile afterFile,
                                       String title,
                                       String documentType) {
        validateFile(beforeFile, "beforeFile");
        validateFile(afterFile, "afterFile");

        String beforeText = extractText(beforeFile);
        String afterText = extractText(afterFile);
        List<DocumentChunker.ChunkData> beforeChunks = chunker.chunk(beforeText);
        List<DocumentChunker.ChunkData> afterChunks = chunker.chunk(afterText);

        List<ReviewCase> cases = new ArrayList<>();
        int alignedCount = Math.max(beforeChunks.size(), afterChunks.size());
        for (int i = 0; i < alignedCount; i++) {
            DocumentChunker.ChunkData before = i < beforeChunks.size() ? beforeChunks.get(i) : null;
            DocumentChunker.ChunkData after = i < afterChunks.size() ? afterChunks.get(i) : null;
            if (!isMeaningfullyDifferent(content(before), content(after))) {
                continue;
            }
            cases.add(diffCase(before, after, beforeFile, afterFile, title, documentType));
        }

        List<ExtractedComment> comments = commentExtractor.extract(afterFile);
        Set<Integer> matchedComments = new HashSet<>();
        for (int i = 0; i < comments.size(); i++) {
            ExtractedComment comment = comments.get(i);
            Optional<ReviewCase> match = findMatchingDiffCase(cases, comment);
            if (match.isPresent()) {
                attachComment(match.get(), comment);
                matchedComments.add(i);
            }
        }

        for (int i = 0; i < comments.size(); i++) {
            if (matchedComments.contains(i)) {
                continue;
            }
            cases.add(commentCase(comments.get(i), beforeFile, afterFile, title, documentType, afterChunks));
        }

        List<ReviewCase> saved = reviewCaseRepository.saveAll(cases);
        log.info("Created {} historical review cases from '{}' and '{}'",
                saved.size(), beforeFile.getOriginalFilename(), afterFile.getOriginalFilename());
        return saved;
    }

    public List<ReviewCase> ingestAnnotated(MultipartFile annotatedFile,
                                            String title,
                                            String documentType) {
        validateFile(annotatedFile, "annotatedFile");

        String text = extractText(annotatedFile);
        List<DocumentChunker.ChunkData> chunks = chunker.chunk(text);
        List<ExtractedComment> comments = commentExtractor.extract(annotatedFile);

        List<ReviewCase> cases = new ArrayList<>();
        for (ExtractedComment comment : comments) {
            ReviewCase reviewCase = new ReviewCase();
            reviewCase.setTitle(blankToNull(title));
            reviewCase.setDocumentType(blankToNull(documentType));
            reviewCase.setSourceDraftFileName(null);
            reviewCase.setSourceReviewedFileName(safeName(annotatedFile));
            reviewCase.setSourceType(ReviewCaseSourceType.REVIEW_COMMENT);
            reviewCase.setReviewerComment(comment.text());
            reviewCase.setCommentAuthor(comment.author());
            reviewCase.setCommentLocation(
                    comment.approximateLocation() == null ? "document-level" : comment.approximateLocation());
            if (comment.referencedText() != null) {
                reviewCase.setReviewedText(truncate(comment.referencedText(), PREVIEW_LIMIT));
            }
            findChunkIndex(chunks, comment.referencedText()).ifPresent(reviewCase::setReviewedChunkIndex);
            cases.add(reviewCase);
        }

        for (RevisionEdit edit : revisionExtractor.extract(annotatedFile)) {
            cases.add(revisionCase(edit, annotatedFile, title, documentType));
        }

        List<ReviewCase> saved = reviewCaseRepository.saveAll(cases);
        log.info("Created {} annotated review cases from '{}'", saved.size(), annotatedFile.getOriginalFilename());
        return saved;
    }

    private ReviewCase revisionCase(RevisionEdit edit,
                                    MultipartFile annotatedFile,
                                    String title,
                                    String documentType) {
        ReviewCase reviewCase = new ReviewCase();
        reviewCase.setTitle(blankToNull(title));
        reviewCase.setDocumentType(blankToNull(documentType));
        reviewCase.setSourceDraftFileName(null);
        reviewCase.setSourceReviewedFileName(safeName(annotatedFile));
        reviewCase.setSourceType(ReviewCaseSourceType.TRACKED_REVISION);
        reviewCase.setCommentAuthor(edit.author());
        reviewCase.setCommentLocation(edit.location());
        if (!edit.originalText().isEmpty()) {
            reviewCase.setOriginalText(truncate(edit.originalText(), PREVIEW_LIMIT));
        }
        if (!edit.insertedText().isEmpty()) {
            reviewCase.setReviewedText(truncate(edit.insertedText(), PREVIEW_LIMIT));
        }
        reviewCase.setDetectedChange(describeRevision(edit));
        return reviewCase;
    }

    private String describeRevision(RevisionEdit edit) {
        return switch (edit.kind()) {
            case REPLACE -> "Replace: '" + truncate(edit.originalText(), 120)
                    + "' → '" + truncate(edit.insertedText(), 120) + "'";
            case INSERT -> "Insert: '" + truncate(edit.insertedText(), 120) + "'";
            case DELETE -> "Delete: '" + truncate(edit.originalText(), 120) + "'";
        };
    }

    public List<ReviewCase> listAll() {
        return reviewCaseRepository.findAllByOrderByCreatedAtDesc();
    }

    public ReviewCase getById(UUID id) {
        return reviewCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ReviewCase not found: " + id));
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private String extractText(MultipartFile file) {
        try {
            String text = tika.parseToString(file.getInputStream());
            return text == null ? "" : text;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from " + file.getOriginalFilename(), e);
        }
    }

    private ReviewCase diffCase(DocumentChunker.ChunkData before,
                                DocumentChunker.ChunkData after,
                                MultipartFile beforeFile,
                                MultipartFile afterFile,
                                String title,
                                String documentType) {
        ReviewCase reviewCase = baseCase(beforeFile, afterFile, title, documentType);
        reviewCase.setSourceType(ReviewCaseSourceType.TEXT_DIFF);
        reviewCase.setOriginalText(truncate(content(before), PREVIEW_LIMIT));
        reviewCase.setReviewedText(truncate(content(after), PREVIEW_LIMIT));
        reviewCase.setDraftChunkIndex(before == null ? null : before.chunkIndex());
        reviewCase.setReviewedChunkIndex(after == null ? null : after.chunkIndex());
        reviewCase.setDetectedChange(describeChange(content(before), content(after)));
        return reviewCase;
    }

    private ReviewCase commentCase(ExtractedComment comment,
                                   MultipartFile beforeFile,
                                   MultipartFile afterFile,
                                   String title,
                                   String documentType,
                                   List<DocumentChunker.ChunkData> afterChunks) {
        ReviewCase reviewCase = baseCase(beforeFile, afterFile, title, documentType);
        reviewCase.setSourceType(ReviewCaseSourceType.REVIEW_COMMENT);
        reviewCase.setReviewerComment(comment.text());
        reviewCase.setReviewedText(truncate(comment.referencedText(), PREVIEW_LIMIT));
        reviewCase.setCommentAuthor(comment.author());
        reviewCase.setCommentLocation(comment.approximateLocation());
        findChunkIndex(afterChunks, comment.referencedText()).ifPresent(reviewCase::setReviewedChunkIndex);
        return reviewCase;
    }

    private ReviewCase baseCase(MultipartFile beforeFile,
                                MultipartFile afterFile,
                                String title,
                                String documentType) {
        ReviewCase reviewCase = new ReviewCase();
        reviewCase.setTitle(blankToNull(title));
        reviewCase.setDocumentType(blankToNull(documentType));
        reviewCase.setSourceDraftFileName(safeName(beforeFile));
        reviewCase.setSourceReviewedFileName(safeName(afterFile));
        return reviewCase;
    }

    private Optional<ReviewCase> findMatchingDiffCase(List<ReviewCase> cases,
                                                     ExtractedComment comment) {
        String referenced = normalize(comment.referencedText());
        if (referenced == null || referenced.length() < 12) {
            return Optional.empty();
        }
        return cases.stream()
                .filter(c -> c.getSourceType() == ReviewCaseSourceType.TEXT_DIFF)
                .filter(c -> containsNormalized(c.getOriginalText(), referenced)
                        || containsNormalized(c.getReviewedText(), referenced))
                .findFirst();
    }

    private void attachComment(ReviewCase reviewCase, ExtractedComment comment) {
        reviewCase.setSourceType(ReviewCaseSourceType.BOTH);
        reviewCase.setReviewerComment(comment.text());
        reviewCase.setCommentAuthor(comment.author());
        reviewCase.setCommentLocation(comment.approximateLocation());
    }

    private Optional<Integer> findChunkIndex(List<DocumentChunker.ChunkData> chunks, String referencedText) {
        String referenced = normalize(referencedText);
        if (referenced == null || referenced.length() < 12) {
            return Optional.empty();
        }
        return chunks.stream()
                .filter(c -> containsNormalized(c.content(), referenced))
                .map(DocumentChunker.ChunkData::chunkIndex)
                .findFirst();
    }

    private boolean isMeaningfullyDifferent(String before, String after) {
        String a = normalize(before);
        String b = normalize(after);
        if (a == null && b == null) {
            return false;
        }
        if (a == null || b == null) {
            return true;
        }
        if (a.equals(b)) {
            return false;
        }
        return changedPercentage(a, b) >= MEANINGFUL_CHANGE_THRESHOLD;
    }

    private String describeChange(String before, String after) {
        ChangedSpan span = changedSpan(before, after);
        return "Changed percentage: " + Math.round(changedPercentage(normalize(before), normalize(after)) * 100.0)
                + "%\nRemoved text: " + valueOrNone(truncate(span.removed(), 260))
                + "\nAdded text: " + valueOrNone(truncate(span.added(), 260));
    }

    private ChangedSpan changedSpan(String before, String after) {
        String a = before == null ? "" : before.trim();
        String b = after == null ? "" : after.trim();
        int prefix = 0;
        int maxPrefix = Math.min(a.length(), b.length());
        while (prefix < maxPrefix && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < a.length() - prefix
                && suffix < b.length() - prefix
                && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) {
            suffix++;
        }
        return new ChangedSpan(
                a.substring(prefix, a.length() - suffix).trim(),
                b.substring(prefix, b.length() - suffix).trim());
    }

    private double changedPercentage(String before, String after) {
        if (before == null && after == null) {
            return 0.0;
        }
        if (before == null || after == null) {
            return 1.0;
        }
        int distance = levenshtein(before, after);
        int maxLength = Math.max(before.length(), after.length());
        return maxLength == 0 ? 0.0 : (double) distance / maxLength;
    }

    private int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private String content(DocumentChunker.ChunkData chunk) {
        return chunk == null ? null : chunk.content();
    }

    private boolean containsNormalized(String haystack, String needle) {
        String normalizedHaystack = normalize(haystack);
        return normalizedHaystack != null && normalizedHaystack.contains(needle);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit - 3) + "...";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private String safeName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "(unnamed)" : name;
    }

    private record ChangedSpan(String removed, String added) {}
}
