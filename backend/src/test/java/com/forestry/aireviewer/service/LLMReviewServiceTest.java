package com.forestry.aireviewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forestry.aireviewer.client.LlmCallException;
import com.forestry.aireviewer.client.LlmClient;
import com.forestry.aireviewer.config.LlmProperties;
import com.forestry.aireviewer.dto.AIReviewCategory;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.AIReviewSeverity;
import com.forestry.aireviewer.prompt.ReviewPromptTemplate;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LLMReviewServiceTest {

    private StubLlmClient llmClient;
    private LLMReviewService service;

    @BeforeEach
    void setUp() {
        llmClient = new StubLlmClient();
        ReviewPromptTemplate promptTemplate = new ReviewPromptTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        LlmProperties props = new LlmProperties();
        props.setProvider("anthropic");
        props.setModel("claude-sonnet-4-6");
        service = new LLMReviewService(llmClient, promptTemplate, objectMapper, validator, props);
    }

    @Test
    @DisplayName("review parses a well-formed JSON response into AIReviewResponse")
    void review_validJson_isParsed() {
        llmClient.respondWith("""
                {
                  "findings": [
                    {
                      "category": "missing_evidence",
                      "severity": "high",
                      "confidence": 0.8,
                      "section": "Section 3.2",
                      "finding": "Baseline biodiversity data missing.",
                      "evidence": "No baseline biodiversity survey is referenced in this chunk.",
                      "recommendation": "Conduct a baseline biodiversity survey.",
                      "sourceReferences": ["EIA Guideline §12.3"]
                    }
                  ]
                }
                """);

        AIReviewResponse response = service.review(sampleRequest());

        assertThat(response.findings()).hasSize(1);
        assertThat(response.findings().get(0).category()).isEqualTo(AIReviewCategory.MISSING_EVIDENCE);
        assertThat(response.findings().get(0).severity()).isEqualTo(AIReviewSeverity.HIGH);
        assertThat(response.findings().get(0).confidence()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("review accepts a JSON response wrapped in markdown code fences")
    void review_jsonInsideCodeFences_isAccepted() {
        llmClient.respondWith("""
                ```json
                {"findings": []}
                ```
                """);

        AIReviewResponse response = service.review(sampleRequest());

        assertThat(response.findings()).isEmpty();
    }

    @Test
    @DisplayName("review accepts an empty findings array")
    void review_emptyFindings_isAccepted() {
        llmClient.respondWith("{\"findings\": []}");

        AIReviewResponse response = service.review(sampleRequest());

        assertThat(response.findings()).isEmpty();
    }

    @Test
    @DisplayName("review fails when the LLM returns non-JSON text")
    void review_invalidJson_throwsInvalidAIReviewException() {
        llmClient.respondWith("the model is happy to tell you about findings but not in json sorry");

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("malformed JSON");
    }

    @Test
    @DisplayName("review fails when JSON is missing required fields")
    void review_missingRequiredField_throwsInvalidAIReviewException() {
        llmClient.respondWith("""
                {
                  "findings": [
                    {
                      "category": "missing_evidence",
                      "severity": "medium",
                      "confidence": 0.5,
                      "section": "Section 3",
                      "finding": "",
                      "evidence": "evidence text",
                      "recommendation": "recommendation text"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("finding is required");
    }

    @Test
    @DisplayName("review fails when JSON contains an unknown category")
    void review_unknownCategory_throwsInvalidAIReviewException() {
        llmClient.respondWith("""
                {
                  "findings": [
                    {
                      "category": "completely_made_up_category",
                      "severity": "high",
                      "confidence": 0.9,
                      "section": "Section 1",
                      "finding": "x",
                      "evidence": "x",
                      "recommendation": "x"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("malformed JSON");
    }

    @Test
    @DisplayName("review fails when confidence is outside the 0..1 range")
    void review_confidenceOutOfRange_throwsInvalidAIReviewException() {
        llmClient.respondWith("""
                {
                  "findings": [
                    {
                      "category": "vague_language",
                      "severity": "low",
                      "confidence": 1.5,
                      "section": "Section 1",
                      "finding": "vague",
                      "evidence": "evidence",
                      "recommendation": "rec"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("review wraps an upstream LlmCallException as InvalidAIReviewException")
    void review_llmCallFails_wrapsException() {
        llmClient.failWith(new LlmCallException("upstream HTTP 503"));

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("LLM call failed")
                .hasMessageContaining("upstream HTTP 503");
    }

    @Test
    @DisplayName("review fails on an empty response from the model")
    void review_emptyResponse_throws() {
        llmClient.respondWith("");

        assertThatThrownBy(() -> service.review(sampleRequest()))
                .isInstanceOf(InvalidAIReviewException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("review passes the rendered prompt to the LLM client and never logs raw chunk content")
    void review_promptContainsChunkPlaceholdersFilledIn() {
        llmClient.respondWith("{\"findings\": []}");
        ReviewRequest req = new ReviewRequest(
                UUID.randomUUID(),
                "feasibility-report.pdf",
                "Section 3.2 Biodiversity. The project includes baseline data collection.",
                1, 3);

        service.review(req);

        String prompt = llmClient.lastPrompt();
        assertThat(prompt).contains("feasibility-report.pdf");
        assertThat(prompt).contains("2 of 3");
        assertThat(prompt).contains("Section 3.2 Biodiversity");
        assertThat(prompt).doesNotContain("{{");
    }

    private ReviewRequest sampleRequest() {
        return new ReviewRequest(
                UUID.randomUUID(),
                "report.pdf",
                "Some chunk content body.",
                0,
                1);
    }

    /** Hand-rolled LlmClient stub — avoids Mockito mocking issues on JDK 25. */
    private static class StubLlmClient implements LlmClient {
        private final AtomicReference<String> lastPrompt = new AtomicReference<>();
        private String nextResponse;
        private RuntimeException nextFailure;

        void respondWith(String response) {
            this.nextResponse = response;
            this.nextFailure = null;
        }

        void failWith(RuntimeException failure) {
            this.nextResponse = null;
            this.nextFailure = failure;
        }

        String lastPrompt() {
            return lastPrompt.get();
        }

        @Override
        public String complete(String prompt) {
            lastPrompt.set(prompt);
            if (nextFailure != null) {
                throw nextFailure;
            }
            return nextResponse;
        }

        @Override
        public String providerName() {
            return "stub";
        }
    }
}
