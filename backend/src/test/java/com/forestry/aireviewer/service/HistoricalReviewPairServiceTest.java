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
    private DocxCommentExtractor commentExtractor;

    private HistoricalReviewPairService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalReviewPairService(reviewCaseRepository, new DocumentChunker(), commentExtractor);
        lenient().when(reviewCaseRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
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
                new DocxCommentExtractor.ExtractedComment(
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
                new DocxCommentExtractor.ExtractedComment(
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

    private MockMultipartFile textFile(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes());
    }

    private MockMultipartFile docxNamedTextFile(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes());
    }
}
