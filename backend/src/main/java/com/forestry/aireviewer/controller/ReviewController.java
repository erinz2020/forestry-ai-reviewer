package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.service.ReviewOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewOrchestrator reviewOrchestrator;

    public ReviewController(ReviewOrchestrator reviewOrchestrator) {
        this.reviewOrchestrator = reviewOrchestrator;
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

    @PatchMapping("/findings/{id}/status")
    public ResponseEntity<Finding> updateStatus(@PathVariable UUID id,
                                                @RequestBody Map<String, String> body) {
        FindingStatus status = FindingStatus.valueOf(body.get("status"));
        Finding updated = reviewOrchestrator.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
