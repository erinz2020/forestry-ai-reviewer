package com.forestry.aireviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocxRevisionExtractorTest {

    private final DocxRevisionExtractor extractor = new DocxRevisionExtractor();

    @Test
    @DisplayName("paired del + ins by same author form a single REPLACE edit")
    void pairedDelIns_sameAuthor_pairedAsReplace() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:del w:id="1" w:author="Alice" w:date="2023-07-26T10:00:00Z">
                    <w:r><w:delText>old phrase</w:delText></w:r>
                  </w:del>
                  <w:ins w:id="2" w:author="Alice" w:date="2023-07-26T10:00:01Z">
                    <w:r><w:t>new phrase</w:t></w:r>
                  </w:ins>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(1);
        RevisionEdit edit = edits.get(0);
        assertThat(edit.author()).isEqualTo("Alice");
        assertThat(edit.originalText()).isEqualTo("old phrase");
        assertThat(edit.insertedText()).isEqualTo("new phrase");
        assertThat(edit.location()).isEqualTo("Paragraph 1");
        assertThat(edit.kind()).isEqualTo(RevisionEdit.Kind.REPLACE);
    }

    @Test
    @DisplayName("standalone insertion becomes one INSERT edit")
    void standaloneInsertion_oneInsertEdit() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:r><w:t>baseline </w:t></w:r>
                  <w:ins w:id="1" w:author="Bob" w:date="2023-07-26T10:00:00Z">
                    <w:r><w:t>added words</w:t></w:r>
                  </w:ins>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(1);
        RevisionEdit edit = edits.get(0);
        assertThat(edit.author()).isEqualTo("Bob");
        assertThat(edit.originalText()).isEmpty();
        assertThat(edit.insertedText()).isEqualTo("added words");
        assertThat(edit.kind()).isEqualTo(RevisionEdit.Kind.INSERT);
    }

    @Test
    @DisplayName("standalone deletion becomes one DELETE edit")
    void standaloneDeletion_oneDeleteEdit() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:del w:id="1" w:author="Bob" w:date="2023-07-26T10:00:00Z">
                    <w:r><w:delText>removed words</w:delText></w:r>
                  </w:del>
                  <w:r><w:t>trailing context</w:t></w:r>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(1);
        RevisionEdit edit = edits.get(0);
        assertThat(edit.originalText()).isEqualTo("removed words");
        assertThat(edit.insertedText()).isEmpty();
        assertThat(edit.kind()).isEqualTo(RevisionEdit.Kind.DELETE);
    }

    @Test
    @DisplayName("del and ins separated by a text run are NOT paired")
    void delAndIns_separatedByTextRun_treatedAsSolo() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:del w:id="1" w:author="Alice" w:date="2023-07-26T10:00:00Z">
                    <w:r><w:delText>old</w:delText></w:r>
                  </w:del>
                  <w:r><w:t>middle text</w:t></w:r>
                  <w:ins w:id="2" w:author="Alice" w:date="2023-07-26T10:00:01Z">
                    <w:r><w:t>new</w:t></w:r>
                  </w:ins>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(2);
        assertThat(edits.get(0).kind()).isEqualTo(RevisionEdit.Kind.DELETE);
        assertThat(edits.get(0).originalText()).isEqualTo("old");
        assertThat(edits.get(1).kind()).isEqualTo(RevisionEdit.Kind.INSERT);
        assertThat(edits.get(1).insertedText()).isEqualTo("new");
    }

    @Test
    @DisplayName("adjacent del + ins with different authors are NOT paired")
    void adjacentDelIns_differentAuthors_notPaired() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:del w:id="1" w:author="Alice" w:date="2023-07-26T10:00:00Z">
                    <w:r><w:delText>old</w:delText></w:r>
                  </w:del>
                  <w:ins w:id="2" w:author="Bob" w:date="2023-07-27T10:00:00Z">
                    <w:r><w:t>new</w:t></w:r>
                  </w:ins>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(2);
        assertThat(edits.get(0).author()).isEqualTo("Alice");
        assertThat(edits.get(0).kind()).isEqualTo(RevisionEdit.Kind.DELETE);
        assertThat(edits.get(1).author()).isEqualTo("Bob");
        assertThat(edits.get(1).kind()).isEqualTo(RevisionEdit.Kind.INSERT);
    }

    @Test
    @DisplayName("revisions across multiple paragraphs track location")
    void multipleParagraphs_locationsTracked() throws Exception {
        String xml = wrap("""
                <w:p>
                  <w:ins w:id="1" w:author="Alice"><w:r><w:t>first edit</w:t></w:r></w:ins>
                </w:p>
                <w:p>
                  <w:r><w:t>plain</w:t></w:r>
                </w:p>
                <w:p>
                  <w:del w:id="2" w:author="Alice"><w:r><w:delText>third edit</w:delText></w:r></w:del>
                </w:p>
                """);

        List<RevisionEdit> edits = extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(edits).hasSize(2);
        assertThat(edits.get(0).location()).isEqualTo("Paragraph 1");
        assertThat(edits.get(1).location()).isEqualTo("Paragraph 3");
    }

    @Test
    @DisplayName("document with no revisions returns empty")
    void noRevisions_returnsEmpty() throws Exception {
        String xml = wrap("""
                <w:p><w:r><w:t>just plain text</w:t></w:r></w:p>
                """);

        assertThat(extractor.parseDocumentXml(xml.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("supports returns true for .docx, false otherwise")
    void supports_matchesDocxOnly() {
        org.springframework.mock.web.MockMultipartFile docx = new org.springframework.mock.web.MockMultipartFile(
                "f", "review.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1});
        org.springframework.mock.web.MockMultipartFile pdf = new org.springframework.mock.web.MockMultipartFile(
                "f", "review.pdf", "application/pdf", new byte[]{1});
        assertThat(extractor.supports(docx)).isTrue();
        assertThat(extractor.supports(pdf)).isFalse();
    }

    private String wrap(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                """
                + body
                + """
                  </w:body>
                </w:document>
                """;
    }
}
