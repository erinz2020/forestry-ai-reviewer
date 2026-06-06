package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.dto.BulkImportFinding;
import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.service.DocumentAnnotationExporter;
import com.forestry.aireviewer.service.ReviewOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewOrchestrator reviewOrchestrator;
    private final DocumentAnnotationExporter annotationExporter;

    public ReviewController(ReviewOrchestrator reviewOrchestrator,
                            DocumentAnnotationExporter annotationExporter) {
        this.reviewOrchestrator = reviewOrchestrator;
        this.annotationExporter = annotationExporter;
    }

    @PostMapping("/documents/{id}/review")
    public ResponseEntity<List<Finding>> reviewDocument(@PathVariable UUID id) {
        List<Finding> findings = reviewOrchestrator.reviewDocument(id);
        return ResponseEntity.ok(findings);
    }

    @GetMapping("/documents/{id}/findings")
    public ResponseEntity<List<Finding>> getFindings(@PathVariable UUID id) {
        List<Finding> findings = reviewOrchestrator.getFindings(id);
        return ResponseEntity.ok(findings);
    }

    @PostMapping("/documents/{id}/findings/bulk-import")
    public ResponseEntity<List<Finding>> bulkImport(@PathVariable UUID id,
                                                    @Valid @RequestBody List<BulkImportFinding> body) {
        try {
            return ResponseEntity.ok(reviewOrchestrator.bulkImport(id, body));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/documents/{id}/export-annotated")
    public ResponseEntity<byte[]> exportAnnotated(@PathVariable UUID id,
                                                  @RequestParam(value = "author", defaultValue = "liujh") String author) {
        try {
            byte[] bytes = annotationExporter.export(id, author);
            String fileName = "annotated-" + id + ".docx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @PatchMapping("/findings/{id}/status")
    public ResponseEntity<Finding> updateStatus(@PathVariable UUID id,
                                                @RequestBody Map<String, String> body) {
        FindingStatus status = FindingStatus.valueOf(body.get("status"));
        Finding updated = reviewOrchestrator.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
