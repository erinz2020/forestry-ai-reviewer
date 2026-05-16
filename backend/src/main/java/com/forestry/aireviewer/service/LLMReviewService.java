package com.forestry.aireviewer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forestry.aireviewer.client.LlmCallException;
import com.forestry.aireviewer.client.LlmClient;
import com.forestry.aireviewer.config.LlmProperties;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.prompt.ReviewPromptTemplate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Real LLM-backed review provider. Active when {@code app.review.provider=llm}.
 *
 * <p>Flow per chunk:</p>
 * <ol>
 *   <li>Render the prompt template against the {@link ReviewRequest}.</li>
 *   <li>Send the prompt to the configured {@link LlmClient}.</li>
 *   <li>Strip any stray markdown fences and parse the response as
 *       {@link AIReviewResponse}.</li>
 *   <li>Run Bean Validation on the parsed response.</li>
 * </ol>
 *
 * <p>Any failure at steps 2–4 throws {@link InvalidAIReviewException}, which
 * the orchestrator surfaces as a failed review — no partial findings are
 * persisted. API keys are never logged; chunk content is never logged.</p>
 */
@Service
@ConditionalOnProperty(name = "app.review.provider", havingValue = "llm")
public class LLMReviewService implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(LLMReviewService.class);

    private final LlmClient llmClient;
    private final ReviewPromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String model;

    public LLMReviewService(LlmClient llmClient,
                            ReviewPromptTemplate promptTemplate,
                            ObjectMapper objectMapper,
                            Validator validator,
                            LlmProperties llmProperties) {
        this.llmClient = llmClient;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.model = llmProperties.getModel();
        log.info("LLMReviewService ready (provider={}, model={})",
                llmClient.providerName(), model);
    }

    @Override
    public AIReviewResponse review(ReviewRequest request) {
        int chunkHuman = request.chunkIndex() + 1;
        log.info("LLM review start documentId={} chunk={}/{} provider={} model={}",
                request.documentId(), chunkHuman, request.totalChunks(),
                llmClient.providerName(), model);

        String prompt = promptTemplate.render(request);

        String raw;
        try {
            raw = llmClient.complete(prompt);
        } catch (LlmCallException e) {
            log.error("LLM call failed documentId={} chunk={}/{}: {}",
                    request.documentId(), chunkHuman, request.totalChunks(), e.getMessage());
            throw new InvalidAIReviewException("LLM call failed: " + e.getMessage());
        }

        AIReviewResponse response = parse(raw, request, chunkHuman);
        validate(response, request, chunkHuman);

        log.info("LLM review success documentId={} chunk={}/{} findings={}",
                request.documentId(), chunkHuman, request.totalChunks(),
                response.findings().size());
        return response;
    }

    private AIReviewResponse parse(String raw, ReviewRequest request, int chunkHuman) {
        String cleaned = stripJsonFences(raw);
        if (cleaned.isEmpty()) {
            log.error("LLM returned empty response documentId={} chunk={}/{}",
                    request.documentId(), chunkHuman, request.totalChunks());
            throw new InvalidAIReviewException("LLM returned an empty response");
        }
        try {
            return objectMapper.readValue(cleaned, AIReviewResponse.class);
        } catch (JsonProcessingException e) {
            log.error("LLM returned malformed JSON documentId={} chunk={}/{}: {}",
                    request.documentId(), chunkHuman, request.totalChunks(),
                    e.getOriginalMessage());
            throw new InvalidAIReviewException(
                    "LLM returned malformed JSON: " + e.getOriginalMessage());
        }
    }

    private void validate(AIReviewResponse response, ReviewRequest request, int chunkHuman) {
        Set<ConstraintViolation<AIReviewResponse>> violations = validator.validate(response);
        if (violations.isEmpty()) {
            return;
        }
        String detail = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        log.error("LLM response failed validation documentId={} chunk={}/{}: {}",
                request.documentId(), chunkHuman, request.totalChunks(), detail);
        throw new InvalidAIReviewException(
                "LLM response failed validation: " + detail);
    }

    /**
     * Defensive fence stripping. The prompt forbids markdown, but real models
     * occasionally wrap output in ```json ... ``` anyway. We strip a single
     * leading fence and a single trailing fence; if the model produced more
     * exotic decoration, JSON parsing will fail and the call is rejected.
     */
    private static String stripJsonFences(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            t = (newline >= 0) ? t.substring(newline + 1) : t.substring(3);
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
