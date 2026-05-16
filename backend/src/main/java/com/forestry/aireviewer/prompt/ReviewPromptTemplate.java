package com.forestry.aireviewer.prompt;

import com.forestry.aireviewer.service.ReviewRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the chunk-aware LLM review prompt from the classpath and renders it
 * for a single {@link ReviewRequest}. The template is loaded once at
 * construction. Rendering is literal string substitution — no templating
 * engine — so brace-like characters inside the chunk are safe.
 */
@Component
public class ReviewPromptTemplate {

    static final String TEMPLATE_PATH = "prompts/review-prompt.txt";
    static final String DOCUMENT_TITLE_PLACEHOLDER = "{{documentTitle}}";
    static final String CHUNK_INDEX_PLACEHOLDER = "{{chunkIndex}}";
    static final String TOTAL_CHUNKS_PLACEHOLDER = "{{totalChunks}}";
    static final String CHUNK_CONTENT_PLACEHOLDER = "{{chunkContent}}";

    private final String template;

    public ReviewPromptTemplate() {
        this.template = loadTemplate();
    }

    public String render(ReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.chunkContent() == null || request.chunkContent().isBlank()) {
            throw new IllegalArgumentException("chunkContent must not be blank");
        }
        String title = (request.documentTitle() == null || request.documentTitle().isBlank())
                ? "untitled" : request.documentTitle();
        return template
                .replace(DOCUMENT_TITLE_PLACEHOLDER, title)
                .replace(CHUNK_INDEX_PLACEHOLDER, String.valueOf(request.chunkIndex() + 1))
                .replace(TOTAL_CHUNKS_PLACEHOLDER, String.valueOf(request.totalChunks()))
                .replace(CHUNK_CONTENT_PLACEHOLDER, request.chunkContent());
    }

    /** Exposed for tests so the raw template can be asserted against. */
    String rawTemplate() {
        return template;
    }

    private static String loadTemplate() {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + TEMPLATE_PATH, e);
        }
    }
}
