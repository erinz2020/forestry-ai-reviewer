package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.model.ReviewCase;
import com.forestry.aireviewer.service.HistoricalReviewPairService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/review-cases")
public class ReviewCaseController {

    private final HistoricalReviewPairService historicalReviewPairService;

    public ReviewCaseController(HistoricalReviewPairService historicalReviewPairService) {
        this.historicalReviewPairService = historicalReviewPairService;
    }

    @PostMapping("/upload-pair")
    public ResponseEntity<List<ReviewCase>> uploadPair(@RequestParam("beforeFile") MultipartFile beforeFile,
                                                       @RequestParam("afterFile") MultipartFile afterFile,
                                                       @RequestParam(value = "title", required = false) String title,
                                                       @RequestParam(value = "documentType", required = false) String documentType) {
        try {
            return ResponseEntity.ok(historicalReviewPairService.ingestPair(beforeFile, afterFile, title, documentType));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/upload-annotated")
    public ResponseEntity<List<ReviewCase>> uploadAnnotated(@RequestParam("annotatedFile") MultipartFile annotatedFile,
                                                            @RequestParam(value = "title", required = false) String title,
                                                            @RequestParam(value = "documentType", required = false) String documentType) {
        try {
            return ResponseEntity.ok(historicalReviewPairService.ingestAnnotated(annotatedFile, title, documentType));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/upload-notes")
    public ResponseEntity<List<ReviewCase>> uploadNotes(@RequestParam("notesFile") MultipartFile notesFile,
                                                        @RequestParam(value = "relatedFileName", required = false) String relatedFileName,
                                                        @RequestParam(value = "title", required = false) String title,
                                                        @RequestParam(value = "documentType", required = false) String documentType) {
        try {
            return ResponseEntity.ok(historicalReviewPairService.ingestReviewerNotes(
                    notesFile, relatedFileName, title, documentType));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping
    public ResponseEntity<List<ReviewCase>> listAll() {
        return ResponseEntity.ok(historicalReviewPairService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReviewCase> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(historicalReviewPairService.getById(id));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void missingRequestParameter() {
    }
}
