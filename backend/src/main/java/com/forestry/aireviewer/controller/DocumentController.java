package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> upload(@RequestParam("file") MultipartFile file) {
        Document doc = documentService.upload(file);
        return ResponseEntity.ok(doc);
    }

    @GetMapping
    public ResponseEntity<List<Document>> listAll() {
        return ResponseEntity.ok(documentService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getById(id));
    }
}
