package com.forestry.aireviewer.controller;

import com.forestry.aireviewer.model.DocumentType;
import com.forestry.aireviewer.repository.DocumentTypeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeController(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    @GetMapping
    public ResponseEntity<List<DocumentType>> listAll() {
        return ResponseEntity.ok(documentTypeRepository.findAllByOrderByCategoryAscNameAsc());
    }
}
