package com.forestry.aireviewer.service;

import org.apache.poi.xwpf.usermodel.XWPFComment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocxCommentExtractor implements CommentExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxCommentExtractor.class);

    @Override
    public boolean supports(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return true;
        }
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equalsIgnoreCase(file.getContentType());
    }

    @Override
    public List<ExtractedComment> extract(MultipartFile file) {
        if (!supports(file)) {
            return List.of();
        }

        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            XWPFComment[] comments = document.getComments();
            if (comments == null || comments.length == 0) {
                return List.of();
            }

            List<ExtractedComment> result = new ArrayList<>();
            for (XWPFComment comment : comments) {
                String text = clean(comment.getText());
                if (text == null) {
                    continue;
                }
                CommentAnchor anchor = findAnchor(document, comment.getId());
                result.add(new ExtractedComment(
                        text,
                        clean(comment.getAuthor()),
                        anchor.referencedText(),
                        anchor.location()));
            }
            log.info("Extracted {} Word comments from '{}'", result.size(), file.getOriginalFilename());
            return result;
        } catch (Exception e) {
            log.warn("Failed to extract Word comments from '{}': {}", file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    private CommentAnchor findAnchor(XWPFDocument document, String commentId) {
        if (commentId == null || commentId.isBlank()) {
            return CommentAnchor.empty();
        }

        List<XWPFParagraph> paragraphs = document.getParagraphs();
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String xml = paragraph.getCTP().xmlText();
            if (xml.contains("commentRangeStart") && xml.contains("w:id=\"" + commentId + "\"")) {
                String text = clean(paragraph.getText());
                return new CommentAnchor(text, "Paragraph " + (i + 1));
            }
        }
        return CommentAnchor.empty();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\0', ' ').trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private record CommentAnchor(String referencedText, String location) {
        static CommentAnchor empty() {
            return new CommentAnchor(null, null);
        }
    }
}
