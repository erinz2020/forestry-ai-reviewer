package com.forestry.aireviewer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.forestry.aireviewer.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions client. Active when review provider is {@code llm}
 * and {@code app.llm.provider=openai}.
 * Reads the API key from the {@code OPENAI_API_KEY} environment variable.
 * Requests JSON mode so the model must return a parseable JSON object.
 */
@Component
@ConditionalOnExpression("'${app.review.provider:mock}' == 'llm' && '${app.llm.provider:anthropic}' == 'openai'")
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public OpenAiLlmClient(LlmProperties properties,
                           @Value("${OPENAI_API_KEY:#{null}}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY environment variable must be set when app.llm.provider=openai");
        }
        this.apiKey = apiKey;
        this.model = properties.getModel();
        this.http = buildHttpClient(properties.getTimeoutSeconds());
        log.info("OpenAiLlmClient ready (model={}, timeoutSeconds={})",
                model, properties.getTimeoutSeconds());
    }

    @Override
    public String complete(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0);

        JsonNode response;
        try {
            response = http.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LlmCallException(
                    "OpenAI API call failed with status " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new LlmCallException("OpenAI API call failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new LlmCallException("OpenAI returned an empty response body");
        }
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || !content.isTextual()) {
            throw new LlmCallException("OpenAI response did not contain choices[0].message.content");
        }
        return content.asText();
    }

    @Override
    public String providerName() {
        return "openai";
    }

    private static RestClient buildHttpClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }
}
