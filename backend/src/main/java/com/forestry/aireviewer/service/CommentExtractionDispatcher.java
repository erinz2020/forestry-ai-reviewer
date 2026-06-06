package com.forestry.aireviewer.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Component
public class CommentExtractionDispatcher {

    private final List<CommentExtractor> extractors;

    public CommentExtractionDispatcher(List<CommentExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public List<ExtractedComment> extract(MultipartFile file) {
        if (file == null) {
            return List.of();
        }
        for (CommentExtractor extractor : extractors) {
            if (extractor.supports(file)) {
                return extractor.extract(file);
            }
        }
        return List.of();
    }
}
