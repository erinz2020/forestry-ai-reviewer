package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.ReviewCase;
import com.forestry.aireviewer.model.ReviewCaseSourceType;
import com.forestry.aireviewer.repository.ReviewCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalReviewPairServiceTest {

    @Mock
    private ReviewCaseRepository reviewCaseRepository;

    @Mock
    private CommentExtractionDispatcher commentExtractor;

    @Mock
    private DocxRevisionExtractor revisionExtractor;

    private HistoricalReviewPairService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalReviewPairService(
                reviewCaseRepository, new DocumentChunker(), commentExtractor, revisionExtractor);
        lenient().when(reviewCaseRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(revisionExtractor.extract(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("upload pair creates review cases from meaningful text diff")
    void ingestPair_textDiff_createsReviewCase() {
        MockMultipartFile before = textFile("draft.txt", "Section 1\n\nThe plan protects 10 hectares.");
        MockMultipartFile after = textFile("reviewed.txt", "Section 1\n\nThe plan protects 15 hectares and adds riparian buffers.");
        when(commentExtractor.extract(after)).thenReturn(List.of());

        List<ReviewCase> cases = service.ingestPair(before, after, "Case A", "Feasibility report");

        assertThat(cases).hasSize(1);
        ReviewCase reviewCase = cases.get(0);
        assertThat(reviewCase.getSourceType()).isEqualTo(ReviewCaseSourceType.TEXT_DIFF);
        assertThat(reviewCase.getTitle()).isEqualTo("Case A");
        assertThat(reviewCase.getDocumentType()).isEqualTo("Feasibility report");
        assertThat(reviewCase.getOriginalText()).contains("10 hectares");
        assertThat(reviewCase.getReviewedText()).contains("15 hectares");
        assertThat(reviewCase.getDetectedChange()).contains("Changed percentage");
    }

    @Test
    @DisplayName("identical documents create no meaningful review cases")
    void ingestPair_identicalDocuments_createsNoCases() {
        MockMultipartFile before = textFile("draft.txt", "Same body text.");
        MockMultipartFile after = textFile("reviewed.txt", "Same body text.");
        when(commentExtractor.extract(after)).thenReturn(List.of());

        List<ReviewCase> cases = service.ingestPair(before, after, null, null);

        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("reviewed docx comments create review cases")
    void ingestPair_docxComments_createReviewCases() {
        MockMultipartFile before = textFile("draft.txt", "The access road crosses a wetland.");
        MockMultipartFile after = docxNamedTextFile("reviewed.docx", "The access road crosses a wetland.");
        when(commentExtractor.extract(after)).thenReturn(List.of(
                new ExtractedComment(
                        "Please cite the wetland survey.",
                        "Reviewer A",
                        "The access road crosses a wetland.",
                        "Paragraph 1")));

        List<ReviewCase> cases = service.ingestPair(before, after, "Comment only", null);

        assertThat(cases).hasSize(1);
        ReviewCase reviewCase = cases.get(0);
        assertThat(reviewCase.getSourceType()).isEqualTo(ReviewCaseSourceType.REVIEW_COMMENT);
        assertThat(reviewCase.getReviewerComment()).isEqualTo("Please cite the wetland survey.");
        assertThat(reviewCase.getCommentAuthor()).isEqualTo("Reviewer A");
        assertThat(reviewCase.getCommentLocation()).isEqualTo("Paragraph 1");
        assertThat(reviewCase.getReviewedChunkIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("comments matching changed text are attached to diff cases")
    void ingestPair_commentMatchingDiff_setsSourceTypeBoth() {
        MockMultipartFile before = textFile("draft.txt", "The buffer is 10 meters.");
        MockMultipartFile after = docxNamedTextFile("reviewed.docx", "The buffer is 30 meters near fish habitat.");
        when(commentExtractor.extract(after)).thenReturn(List.of(
                new ExtractedComment(
                        "Good, this now matches the habitat guidance.",
                        "Reviewer A",
                        "The buffer is 30 meters near fish habitat.",
                        "Paragraph 1")));

        List<ReviewCase> cases = service.ingestPair(before, after, null, null);

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).getSourceType()).isEqualTo(ReviewCaseSourceType.BOTH);
        assertThat(cases.get(0).getReviewerComment()).contains("matches the habitat guidance");
    }

    @Test
    @DisplayName("text changes without comments still create review cases")
    void ingestPair_textChangeWithoutComments_createsDiffCase() {
        MockMultipartFile before = textFile("draft.txt", "Baseline survey was completed in winter.");
        MockMultipartFile after = textFile("reviewed.txt", "Baseline survey was completed in spring and summer.");
        when(commentExtractor.extract(after)).thenReturn(List.of());

        List<ReviewCase> cases = service.ingestPair(before, after, null, null);

        assertThat(cases).extracting(ReviewCase::getSourceType)
                .containsExactly(ReviewCaseSourceType.TEXT_DIFF);
    }

    @Test
    @DisplayName("missing beforeFile or afterFile returns validation error before persistence")
    void ingestPair_missingFile_rejectsRequest() {
        MockMultipartFile after = textFile("reviewed.txt", "Body");

        assertThatThrownBy(() -> service.ingestPair(null, after, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("beforeFile is required");

        verify(reviewCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("ingestAnnotated stores all comments as REVIEW_COMMENT cases")
    void ingestAnnotated_anchoredAndFloatingComments_storedAsReviewComments() {
        MockMultipartFile annotated = textFile("reviewed.docx",
                "Section 1\n\nThe access road crosses a wetland.\n\nSection 2\n\nMitigation plan is vague.");
        when(commentExtractor.extract(annotated)).thenReturn(List.of(
                new ExtractedComment("Cite the wetland survey.", "Reviewer A",
                        "The access road crosses a wetland.", "Paragraph 2"),
                new ExtractedComment("Be specific about species.", "Reviewer A",
                        null, null)));

        List<ReviewCase> cases = service.ingestAnnotated(annotated, "Wetland EIA", "EIA");

        assertThat(cases).hasSize(2);
        assertThat(cases).allMatch(c -> c.getSourceType() == ReviewCaseSourceType.REVIEW_COMMENT);

        ReviewCase anchored = cases.stream()
                .filter(c -> c.getReviewerComment().contains("wetland survey"))
                .findFirst().orElseThrow();
        assertThat(anchored.getReviewedChunkIndex()).isEqualTo(0);
        assertThat(anchored.getCommentLocation()).isEqualTo("Paragraph 2");
        assertThat(anchored.getSourceDraftFileName()).isNull();
        assertThat(anchored.getSourceReviewedFileName()).isEqualTo("reviewed.docx");
        assertThat(anchored.getReviewedText()).contains("wetland");
        assertThat(anchored.getTitle()).isEqualTo("Wetland EIA");
        assertThat(anchored.getDocumentType()).isEqualTo("EIA");

        ReviewCase floating = cases.stream()
                .filter(c -> c.getReviewerComment().contains("Be specific"))
                .findFirst().orElseThrow();
        assertThat(floating.getReviewedChunkIndex()).isNull();
        assertThat(floating.getCommentLocation()).isEqualTo("document-level");
        assertThat(floating.getReviewedText()).isNull();
    }

    @Test
    @DisplayName("ingestAnnotated with no comments stores nothing")
    void ingestAnnotated_noComments_returnsEmpty() {
        MockMultipartFile annotated = textFile("reviewed.docx", "Body without comments.");
        when(commentExtractor.extract(annotated)).thenReturn(List.of());

        List<ReviewCase> cases = service.ingestAnnotated(annotated, null, null);

        assertThat(cases).isEmpty();
    }

    @Test
    @DisplayName("ingestAnnotated stores tracked revisions as TRACKED_REVISION cases")
    void ingestAnnotated_trackedRevisions_storedAsTrackedRevisionCases() {
        MockMultipartFile annotated = textFile("reviewed.docx", "Section 1\n\nBody.");
        when(commentExtractor.extract(annotated)).thenReturn(List.of());
        when(revisionExtractor.extract(annotated)).thenReturn(List.of(
                new RevisionEdit("Alice", "old phrase", "new phrase", "Paragraph 3"),
                new RevisionEdit("Bob", "", "added context", "Paragraph 5"),
                new RevisionEdit("Bob", "removed words", "", "Paragraph 7")));

        List<ReviewCase> cases = service.ingestAnnotated(annotated, "Wetland EIA", "EIA");

        assertThat(cases).hasSize(3);
        assertThat(cases).allMatch(c -> c.getSourceType() == ReviewCaseSourceType.TRACKED_REVISION);

        ReviewCase replace = cases.get(0);
        assertThat(replace.getCommentAuthor()).isEqualTo("Alice");
        assertThat(replace.getCommentLocation()).isEqualTo("Paragraph 3");
        assertThat(replace.getOriginalText()).isEqualTo("old phrase");
        assertThat(replace.getReviewedText()).isEqualTo("new phrase");
        assertThat(replace.getDetectedChange()).contains("Replace");

        ReviewCase insert = cases.get(1);
        assertThat(insert.getOriginalText()).isNull();
        assertThat(insert.getReviewedText()).isEqualTo("added context");
        assertThat(insert.getDetectedChange()).contains("Insert");

        ReviewCase delete = cases.get(2);
        assertThat(delete.getOriginalText()).isEqualTo("removed words");
        assertThat(delete.getReviewedText()).isNull();
        assertThat(delete.getDetectedChange()).contains("Delete");
    }

    @Test
    @DisplayName("ingestAnnotated skips when reviewed file name already ingested")
    void ingestAnnotated_duplicateFileName_skippedSilently() {
        MockMultipartFile annotated = textFile("reviewed.docx", "body");
        when(reviewCaseRepository.existsBySourceReviewedFileName("reviewed.docx")).thenReturn(true);

        List<ReviewCase> cases = service.ingestAnnotated(annotated, null, null);

        assertThat(cases).isEmpty();
        verify(reviewCaseRepository, never()).saveAll(any());
        verify(commentExtractor, never()).extract(any());
        verify(revisionExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("ingestPair skips when reviewed file name already ingested")
    void ingestPair_duplicateReviewedFileName_skippedSilently() {
        MockMultipartFile before = textFile("draft.txt", "old");
        MockMultipartFile after = textFile("reviewed.txt", "new");
        when(reviewCaseRepository.existsBySourceReviewedFileName("reviewed.txt")).thenReturn(true);

        List<ReviewCase> cases = service.ingestPair(before, after, null, null);

        assertThat(cases).isEmpty();
        verify(reviewCaseRepository, never()).saveAll(any());
        verify(commentExtractor, never()).extract(any());
    }

    @Test
    @DisplayName("ingestAnnotated rejects missing file")
    void ingestAnnotated_missingFile_rejectsRequest() {
        assertThatThrownBy(() -> service.ingestAnnotated(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("annotatedFile is required");

        verify(reviewCaseRepository, never()).saveAll(any());
    }

    private MockMultipartFile textFile(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes());
    }

    private MockMultipartFile docxNamedTextFile(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes());
    }
}
