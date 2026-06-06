package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.repository.FindingRepository;
import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFComments;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTComment;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkup;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Takes a finalized review (CONFIRMED findings) and writes them back into the
 * original .docx as native Word comments, so reviewers see them in Word the
 * same way human review marks appear.
 *
 * <p>Anchor strategy: each finding's {@code quote} is searched for in the
 * document; the comment marker is placed in the first paragraph that contains
 * it. If no paragraph matches, the marker goes on the document's first
 * paragraph as a fallback (and the comment body still references the missing
 * quote so the reviewer can locate it).</p>
 */
@Component
public class DocumentAnnotationExporter {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnnotationExporter.class);

    private static final String COMMENT_REFERENCE_STYLE = "CommentReference";

    private final FindingRepository findingRepository;
    private final DocumentService documentService;

    public DocumentAnnotationExporter(FindingRepository findingRepository,
                                      DocumentService documentService) {
        this.findingRepository = findingRepository;
        this.documentService = documentService;
    }

    public byte[] export(UUID documentId, String author) {
        Document document = documentService.getById(documentId);
        List<Finding> findings = findingRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .filter(f -> f.getStatus() == FindingStatus.CONFIRMED)
                .toList();
        return export(Path.of(document.getOriginalFilePath()), findings, author);
    }

    public byte[] export(Path originalFile, List<Finding> findings, String author) {
        if (author == null || author.isBlank()) {
            author = "liujh";
        }
        try (FileInputStream fis = new FileInputStream(originalFile.toFile());
             XWPFDocument doc = new XWPFDocument(fis);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XWPFComments comments = doc.createComments();
            BigInteger nextId = nextCommentId(comments);

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            if (paragraphs.isEmpty()) {
                throw new IllegalStateException("Document has no body paragraphs to anchor comments to");
            }

            for (Finding finding : findings) {
                XWPFParagraph anchor = findAnchorParagraph(paragraphs, finding.getQuote());
                if (anchor == null) {
                    anchor = paragraphs.get(0);
                }
                writeComment(comments, nextId, author, finding);
                attachCommentMarker(anchor, nextId);
                nextId = nextId.add(BigInteger.ONE);
            }

            doc.write(out);
            byte[] bytes = out.toByteArray();
            log.info("Exported annotated docx for document '{}': {} findings, {} bytes",
                    originalFile.getFileName(), findings.size(), bytes.length);
            return bytes;
        } catch (Exception e) {
            throw new RuntimeException("Failed to export annotated document: " + e.getMessage(), e);
        }
    }

    private XWPFParagraph findAnchorParagraph(List<XWPFParagraph> paragraphs, String quote) {
        if (quote == null || quote.isBlank()) {
            return null;
        }
        String needle = quote.trim();
        if (needle.length() < 4) {
            return null;
        }
        for (XWPFParagraph p : paragraphs) {
            String text = p.getText();
            if (text != null && text.contains(needle)) {
                return p;
            }
        }
        // Try a softer match using the first 40 characters
        String shortNeedle = needle.length() > 40 ? needle.substring(0, 40) : needle;
        for (XWPFParagraph p : paragraphs) {
            String text = p.getText();
            if (text != null && text.contains(shortNeedle)) {
                return p;
            }
        }
        return null;
    }

    private void writeComment(XWPFComments comments, BigInteger id, String author, Finding finding) {
        XWPFComment comment = comments.createComment(id);
        comment.setAuthor(author);
        comment.setInitials(initials(author));
        CTComment ct = comment.getCtComment();
        ct.setDate(Calendar.getInstance());

        for (String line : commentLines(finding)) {
            CTP p = ct.addNewP();
            CTR r = p.addNewR();
            CTText t = r.addNewT();
            t.setStringValue(line);
        }
    }

    private void attachCommentMarker(XWPFParagraph paragraph, BigInteger id) {
        CTP ctp = paragraph.getCTP();

        CTMarkup start = ctp.addNewCommentRangeStart();
        start.setId(id);

        CTMarkup end = ctp.addNewCommentRangeEnd();
        end.setId(id);

        CTR ref = ctp.addNewR();
        CTRPr rpr = ref.addNewRPr();
        CTString style = rpr.addNewRStyle();
        style.setVal(COMMENT_REFERENCE_STYLE);
        ref.addNewCommentReference().setId(id);
    }

    private BigInteger nextCommentId(XWPFComments comments) {
        BigInteger max = BigInteger.valueOf(-1);
        if (comments.getComments() != null) {
            for (XWPFComment c : comments.getComments()) {
                String idValue = c.getId();
                if (idValue == null || idValue.isBlank()) {
                    continue;
                }
                try {
                    BigInteger id = new BigInteger(idValue.trim());
                    if (id.compareTo(max) > 0) {
                        max = id;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max.add(BigInteger.ONE);
    }

    private List<String> commentLines(Finding finding) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String header = "【" + finding.getSeverity().name() + "】 "
                + (finding.getType() == null ? "" : finding.getType().name() + " ")
                + (finding.getLocation() == null ? "" : "[" + finding.getLocation() + "]");
        lines.add(header.trim());
        if (finding.getDescription() != null && !finding.getDescription().isBlank()) {
            lines.add("问题：" + finding.getDescription());
        }
        if (finding.getQuote() != null && !finding.getQuote().isBlank()) {
            String quote = finding.getQuote().trim();
            if (quote.length() > 200) {
                quote = quote.substring(0, 200) + "...";
            }
            lines.add("原文：" + quote);
        }
        if (finding.getSuggestion() != null && !finding.getSuggestion().isBlank()) {
            lines.add("建议：" + finding.getSuggestion());
        }
        if (finding.getEvidence() != null && !finding.getEvidence().isBlank()) {
            lines.add("依据：" + finding.getEvidence());
        }
        return lines;
    }

    private String initials(String author) {
        if (author == null || author.isBlank()) {
            return "?";
        }
        String trimmed = author.trim();
        if (trimmed.codePointAt(0) < 128) {
            return trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase(Locale.ROOT);
        }
        return trimmed.substring(0, 1);
    }
}
