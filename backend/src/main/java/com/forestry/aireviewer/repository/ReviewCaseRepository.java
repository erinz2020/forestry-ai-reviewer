package com.forestry.aireviewer.repository;

import com.forestry.aireviewer.model.ReviewCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewCaseRepository extends JpaRepository<ReviewCase, UUID> {
    List<ReviewCase> findAllByOrderByCreatedAtDesc();

    boolean existsBySourceReviewedFileName(String sourceReviewedFileName);
}
