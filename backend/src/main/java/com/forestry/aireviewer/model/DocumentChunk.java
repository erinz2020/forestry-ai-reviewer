package com.forestry.aireviewer.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A bounded slice of a document's extracted text, produced at upload time so
 * downstream review can iterate chunk-by-chunk instead of sending the whole
 * document to the LLM in a single call.
 *
 * <p>{@code startOffset} and {@code endOffset} are character offsets into the
 * document's {@code extractedText}, used to locate evidence back in the source.</p>
 */
@Entity
@Table(name = "document_chunks",
        indexes = @Index(name = "idx_chunks_document", columnList = "documentId, chunkIndex"))
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private int startOffset;

    @Column(nullable = false)
    private int endOffset;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public DocumentChunk() {}

    public DocumentChunk(UUID documentId, int chunkIndex, String content, int startOffset, int endOffset) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getStartOffset() { return startOffset; }
    public void setStartOffset(int startOffset) { this.startOffset = startOffset; }

    public int getEndOffset() { return endOffset; }
    public void setEndOffset(int endOffset) { this.endOffset = endOffset; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
