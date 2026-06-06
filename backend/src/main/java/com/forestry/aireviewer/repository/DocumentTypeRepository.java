package com.forestry.aireviewer.repository;

import com.forestry.aireviewer.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    List<DocumentType> findAllByOrderByCategoryAscNameAsc();

    Optional<DocumentType> findByCode(String code);
}
