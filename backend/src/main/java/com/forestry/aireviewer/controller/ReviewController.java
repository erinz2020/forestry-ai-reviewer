package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.model.Finding;
import com.forestry.aireviewer.model.FindingStatus;
import com.forestry.aireviewer.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/documents/{id}/review")
    public ResponseEntity<List<Finding>> reviewDocument(@PathVariable UUID id) {
        List<Finding> findings = reviewService.reviewDocument(id);
        return ResponseEntity.ok(findings);
    }

    @GetMapping("/documents/{id}/findings")
    public ResponseEntity<List<Finding>> getFindings(@PathVariable UUID id) {
        List<Finding> findings = reviewService.getFindings(id);
        return ResponseEntity.ok(findings);
    }

    @PatchMapping("/findings/{id}/status")
    public ResponseEntity<Finding> updateStatus(@PathVariable UUID id,
                                                @RequestBody Map<String, String> body) {
        FindingStatus status = FindingStatus.valueOf(body.get("status"));
        Finding updated = reviewService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }
}
