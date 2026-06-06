package com.forestry.aireviewer.service;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PdfCommentExtractor implements CommentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfCommentExtractor.class);

    @Override
    public boolean supports(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return true;
        }
        return "application/pdf".equalsIgnoreCase(file.getContentType());
    }

    @Override
    public List<ExtractedComment> extract(MultipartFile file) {
        if (!supports(file)) {
            return List.of();
        }

        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            List<ExtractedComment> out = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < doc.getNumberOfPages(); pageIndex++) {
                PDPage page = doc.getPage(pageIndex);
                List<PDAnnotation> annotations = page.getAnnotations();
                if (annotations == null || annotations.isEmpty()) {
                    continue;
                }
                for (PDAnnotation annotation : annotations) {
                    String contents = clean(annotation.getContents());
                    if (contents == null) {
                        continue;
                    }
                    String author = clean(annotation.getCOSObject().getString(COSName.T));
                    String referenced = null;
                    if (annotation instanceof PDAnnotationTextMarkup markup) {
                        referenced = extractMarkupText(page, markup);
                    }
                    out.add(new ExtractedComment(
                            contents,
                            author,
                            referenced,
                            "Page " + (pageIndex + 1)));
                }
            }
            log.info("Extracted {} PDF annotations from '{}'", out.size(), file.getOriginalFilename());
            return out;
        } catch (Exception e) {
            log.warn("Failed to extract PDF annotations from '{}': {}", file.getOriginalFilename(), e.getMessage());
            return List.of();
        }
    }

    private String extractMarkupText(PDPage page, PDAnnotationTextMarkup markup) {
        float[] quads = markup.getQuadPoints();
        if (quads == null || quads.length < 8) {
            return null;
        }
        try {
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            float pageHeight = page.getMediaBox().getHeight();
            int regions = 0;
            for (int i = 0; i + 8 <= quads.length; i += 8) {
                float minX = min4(quads[i], quads[i + 2], quads[i + 4], quads[i + 6]);
                float maxX = max4(quads[i], quads[i + 2], quads[i + 4], quads[i + 6]);
                float minY = min4(quads[i + 1], quads[i + 3], quads[i + 5], quads[i + 7]);
                float maxY = max4(quads[i + 1], quads[i + 3], quads[i + 5], quads[i + 7]);
                Rectangle2D.Float rect = new Rectangle2D.Float(
                        minX,
                        pageHeight - maxY,
                        maxX - minX,
                        maxY - minY);
                stripper.addRegion("q" + i, rect);
                regions++;
            }
            if (regions == 0) {
                return null;
            }
            stripper.extractRegions(page);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i + 8 <= quads.length; i += 8) {
                String t = stripper.getTextForRegion("q" + i);
                if (t != null && !t.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(t);
                }
            }
            return clean(sb.toString());
        } catch (Exception e) {
            log.debug("Could not extract markup text from quad points: {}", e.getMessage());
            return null;
        }
    }

    private float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\0', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
    }
}
