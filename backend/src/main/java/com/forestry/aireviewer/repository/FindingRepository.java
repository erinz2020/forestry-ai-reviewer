package com.forestry.aireviewer.repository;

import com.forestry.aireviewer.model.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
