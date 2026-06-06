package com.forestry.aireviewer.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_cases")
public class ReviewCase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column
    private String title;

    @Column
    private String documentType;

    @Column
    private String sectionType;

    @Column(columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String reviewedText;

    @Column(columnDefinition = "TEXT")
    private String reviewerComment;

    @Column(columnDefinition = "TEXT")
    private String detectedChange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private ReviewCaseSourceType sourceType;

    @Column
    private String sourceDraftFileName;

    @Column(nullable = false)
    private String sourceReviewedFileName;

    @Column
    private Integer draftChunkIndex;

    @Column
    private Integer reviewedChunkIndex;

    @Column
    private String commentAuthor;

    @Column
    private String commentLocation;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public String getReviewedText() { return reviewedText; }
    public void setReviewedText(String reviewedText) { this.reviewedText = reviewedText; }

    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }

    public String getDetectedChange() { return detectedChange; }
    public void setDetectedChange(String detectedChange) { this.detectedChange = detectedChange; }

    public ReviewCaseSourceType getSourceType() { return sourceType; }
    public void setSourceType(ReviewCaseSourceType sourceType) { this.sourceType = sourceType; }

    public String getSourceDraftFileName() { return sourceDraftFileName; }
    public void setSourceDraftFileName(String sourceDraftFileName) { this.sourceDraftFileName = sourceDraftFileName; }

    public String getSourceReviewedFileName() { return sourceReviewedFileName; }
    public void setSourceReviewedFileName(String sourceReviewedFileName) { this.sourceReviewedFileName = sourceReviewedFileName; }

    public Integer getDraftChunkIndex() { return draftChunkIndex; }
    public void setDraftChunkIndex(Integer draftChunkIndex) { this.draftChunkIndex = draftChunkIndex; }

    public Integer getReviewedChunkIndex() { return reviewedChunkIndex; }
    public void setReviewedChunkIndex(Integer reviewedChunkIndex) { this.reviewedChunkIndex = reviewedChunkIndex; }

    public String getCommentAuthor() { return commentAuthor; }
    public void setCommentAuthor(String commentAuthor) { this.commentAuthor = commentAuthor; }

    public String getCommentLocation() { return commentLocation; }
    public void setCommentLocation(String commentLocation) { this.commentLocation = commentLocation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
