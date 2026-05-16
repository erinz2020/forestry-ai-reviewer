package com.forestry.aireviewer.service;

import com.forestry.aireviewer.model.Document;
import com.forestry.aireviewer.model.DocumentChunk;
import com.forestry.aireviewer.model.DocumentStatus;
import com.forestry.aireviewer.repository.DocumentChunkRepository;
import com.forestry.aireviewer.repository.DocumentRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunker chunker;
    private final Tika tika = new Tika();

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    private Path uploadPath;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           DocumentChunker chunker) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
    }

    @PostConstruct
    void init() throws IOException {
        uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        log.info("Upload directory: {}", uploadPath);
    }

    public Document upload(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID() + ext;

        Path filePath = uploadPath.resolve(storedName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalName, e);
        }

        Document doc = new Document();
        doc.setFileName(originalName);
        doc.setContentType(file.getContentType());
        doc.setOriginalFilePath(filePath.toString());
        doc.setStatus(DocumentStatus.UPLOADING);
        doc = documentRepository.save(doc);

        extractText(doc);
        doc = documentRepository.save(doc);

        if (doc.getStatus() == DocumentStatus.READY) {
            saveChunks(doc);
        }
        return doc;
    }

    private void extractText(Document doc) {
        doc.setStatus(DocumentStatus.EXTRACTING);
        try {
            String text = tika.parseToString(Path.of(doc.getOriginalFilePath()));
            doc.setExtractedText(text);
            doc.setStatus(DocumentStatus.READY);
            log.info("Extracted {} characters from '{}'", text.length(), doc.getFileName());
        } catch (Exception e) {
            doc.setStatus(DocumentStatus.FAILED);
            log.error("Text extraction failed for '{}': {}", doc.getFileName(), e.getMessage());
        }
    }

    private void saveChunks(Document doc) {
        List<DocumentChunker.ChunkData> data = chunker.chunk(doc.getExtractedText());
        if (data.isEmpty()) {
            log.warn("Document {} produced no chunks despite extracted text", doc.getId());
            return;
        }
        List<DocumentChunk> chunks = new ArrayList<>(data.size());
        for (DocumentChunker.ChunkData d : data) {
            chunks.add(new DocumentChunk(doc.getId(), d.chunkIndex(), d.content(), d.startOffset(), d.endOffset()));
        }
        chunkRepository.saveAll(chunks);
        log.info("Saved {} chunks for document '{}' ({} total characters)",
                chunks.size(), doc.getFileName(), doc.getExtractedText().length());
    }

    public List<Document> listAll() {
        return documentRepository.findAll();
    }

    public Document getById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }
}
