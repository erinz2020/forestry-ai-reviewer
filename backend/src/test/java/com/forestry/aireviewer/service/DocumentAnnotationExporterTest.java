package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingSeverity;
import com.forestry.aireviewer.model.FindingType;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAnnotationExporterTest {

    private final DocumentAnnotationExporter exporter = new DocumentAnnotationExporter(null, null);

    @Test
    @DisplayName("export inserts a Word comment anchored to the paragraph containing the quote")
    void export_anchorsCommentToParagraphContainingQuote() throws Exception {
        Path docx = Files.createTempFile("annotated-test-", ".docx");
        try {
            writeSampleDocx(docx);

            Finding finding = sampleFinding(
                    "面积矛盾",
                    "土地总面积",
                    "土地总面积与分项之和不符");

            byte[] result = exporter.export(docx, List.of(finding), "liujh");

            try (XWPFDocument output = new XWPFDocument(new ByteArrayInputStream(result))) {
                XWPFComment[] comments = output.getComments();
                assertThat(comments).hasSize(1);
                XWPFComment comment = comments[0];
                assertThat(comment.getAuthor()).isEqualTo("liujh");
                String body = comment.getText();
                assertThat(body).contains("面积矛盾");
                assertThat(body).contains("HIGH");
                assertThat(body).contains("土地总面积");
            }
        } finally {
            Files.deleteIfExists(docx);
        }
    }

    @Test
    @DisplayName("findings without a matching quote fall back to the first paragraph")
    void export_unmatchedQuote_fallsBackToFirstParagraph() throws Exception {
        Path docx = Files.createTempFile("annotated-test-", ".docx");
        try {
            writeSampleDocx(docx);

            Finding finding = sampleFinding(
                    "缺少引用",
                    "全文未出现的字符串-1234567890",
                    "引用的原文找不到");

            byte[] result = exporter.export(docx, List.of(finding), "liujh");

            try (XWPFDocument output = new XWPFDocument(new ByteArrayInputStream(result))) {
                XWPFComment[] comments = output.getComments();
                assertThat(comments).hasSize(1);
                assertThat(comments[0].getText()).contains("缺少引用");
            }
        } finally {
            Files.deleteIfExists(docx);
        }
    }

    @Test
    @DisplayName("exporting multiple findings produces a comment per finding")
    void export_multipleFindings_oneCommentPerFinding() throws Exception {
        Path docx = Files.createTempFile("annotated-test-", ".docx");
        try {
            writeSampleDocx(docx);

            Finding a = sampleFinding("问题A", "土地总面积", "A");
            Finding b = sampleFinding("问题B", "森林覆盖率", "B");
            Finding c = sampleFinding("问题C", "土地总面积", "C");

            byte[] result = exporter.export(docx, List.of(a, b, c), "liujh");

            try (XWPFDocument output = new XWPFDocument(new ByteArrayInputStream(result))) {
                XWPFComment[] comments = output.getComments();
                assertThat(comments).hasSize(3);
            }
        } finally {
            Files.deleteIfExists(docx);
        }
    }

    private void writeSampleDocx(Path target) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream tmp = new ByteArrayOutputStream()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("1.1.4 土地利用：全场土地总面积12792.5公顷。");
            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText("1.2.1 森林面积：森林覆盖率96.85%。");
            doc.write(tmp);
            Files.write(target, tmp.toByteArray());
        }
    }

    private Finding sampleFinding(String description, String quote, String suggestion) {
        Finding f = new Finding();
        f.setType(FindingType.INTERNAL_CONTRADICTION);
        f.setSeverity(FindingSeverity.HIGH);
        f.setLocation("1.1.4 节");
        f.setQuote(quote);
        f.setDescription(description);
        f.setSuggestion(suggestion);
        f.setEvidence("交叉核验");
        return f;
    }
}
