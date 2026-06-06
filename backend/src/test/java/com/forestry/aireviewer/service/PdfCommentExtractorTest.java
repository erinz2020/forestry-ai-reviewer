package com.forestry.aireviewer.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfCommentExtractorTest {

    private final PdfCommentExtractor extractor = new PdfCommentExtractor();

    @Test
    @DisplayName("supports returns true for .pdf extension")
    void supports_pdfExtension_returnsTrue() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[]{1});
        assertThat(extractor.supports(file)).isTrue();
    }

    @Test
    @DisplayName("supports returns true for application/pdf content type")
    void supports_pdfContentType_returnsTrue() {
        MockMultipartFile file = new MockMultipartFile("file", "report", "application/pdf", new byte[]{1});
        assertThat(extractor.supports(file)).isTrue();
    }

    @Test
    @DisplayName("supports returns false for non-PDF files")
    void supports_docx_returnsFalse() {
        MockMultipartFile file = new MockMultipartFile("file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1});
        assertThat(extractor.supports(file)).isFalse();
    }

    @Test
    @DisplayName("extract returns empty list for non-PDF files")
    void extract_nonPdf_returnsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1});
        assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    @DisplayName("extract reads highlight annotation contents, author, page number")
    void extract_highlight_returnsCommentWithMetadata() throws Exception {
        byte[] pdf = buildPdfWithAnnotations();
        MockMultipartFile file = new MockMultipartFile("file", "annotated.pdf", "application/pdf", pdf);

        List<ExtractedComment> comments = extractor.extract(file);

        assertThat(comments).hasSize(2);
        ExtractedComment highlight = comments.stream()
                .filter(c -> c.text().contains("geotechnical"))
                .findFirst().orElseThrow();
        assertThat(highlight.text()).isEqualTo("Cite the geotechnical survey.");
        assertThat(highlight.author()).isEqualTo("Reviewer B");
        assertThat(highlight.approximateLocation()).isEqualTo("Page 1");
    }

    @Test
    @DisplayName("extract reads sticky note with null referencedText")
    void extract_stickyNote_returnsNullReferencedText() throws Exception {
        byte[] pdf = buildPdfWithAnnotations();
        MockMultipartFile file = new MockMultipartFile("file", "annotated.pdf", "application/pdf", pdf);

        List<ExtractedComment> comments = extractor.extract(file);

        ExtractedComment sticky = comments.stream()
                .filter(c -> c.text().contains("General concern"))
                .findFirst().orElseThrow();
        assertThat(sticky.referencedText()).isNull();
        assertThat(sticky.author()).isEqualTo("Reviewer C");
        assertThat(sticky.approximateLocation()).isEqualTo("Page 1");
    }

    @Test
    @DisplayName("extract returns empty list for PDF without annotations")
    void extract_pdfWithoutAnnotations_returnsEmpty() throws Exception {
        byte[] pdf = buildPlainPdf();
        MockMultipartFile file = new MockMultipartFile("file", "plain.pdf", "application/pdf", pdf);

        assertThat(extractor.extract(file)).isEmpty();
    }

    @Test
    @DisplayName("extract skips annotations without contents")
    void extract_annotationWithoutContents_skipped() throws Exception {
        byte[] pdf = buildPdfWithEmptyAnnotation();
        MockMultipartFile file = new MockMultipartFile("file", "annotated.pdf", "application/pdf", pdf);

        assertThat(extractor.extract(file)).isEmpty();
    }

    private byte[] buildPdfWithAnnotations() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Erosion on slope must be assessed.");
                cs.endText();
            }

            PDAnnotationTextMarkup highlight =
                    new PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT);
            highlight.setContents("Cite the geotechnical survey.");
            highlight.getCOSObject().setString(COSName.T, "Reviewer B");
            highlight.setRectangle(new PDRectangle(72, 695, 200, 18));
            highlight.setQuadPoints(new float[]{72f, 713f, 272f, 713f, 72f, 695f, 272f, 695f});
            page.getAnnotations().add(highlight);

            PDAnnotationText sticky = new PDAnnotationText();
            sticky.setContents("General concern about wetlands.");
            sticky.getCOSObject().setString(COSName.T, "Reviewer C");
            sticky.setRectangle(new PDRectangle(400, 700, 20, 20));
            page.getAnnotations().add(sticky);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPlainPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildPdfWithEmptyAnnotation() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDAnnotationText empty = new PDAnnotationText();
            empty.setRectangle(new PDRectangle(100, 700, 20, 20));
            page.getAnnotations().add(empty);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
