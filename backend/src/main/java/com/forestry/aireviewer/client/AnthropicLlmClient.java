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
 * Anthropic Messages API client. Active when review provider is {@code llm}
 * and {@code app.llm.provider=anthropic}.
 * Reads the API key from the {@code ANTHROPIC_API_KEY} environment variable
 * (resolved by Spring's property mechanism, so {@code @TestPropertySource}
 * can override it). Fails fast at construction if the key is missing.
 */
@Component
@ConditionalOnExpression("'${app.review.provider:mock}' == 'llm' && '${app.llm.provider:anthropic}' == 'anthropic'")
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final RestClient http;

    public AnthropicLlmClient(LlmProperties properties,
                              @Value("${ANTHROPIC_API_KEY:#{null}}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY environment variable must be set when app.llm.provider=anthropic");
        }
        this.apiKey = apiKey;
        this.model = properties.getModel();
        this.maxTokens = properties.getMaxOutputTokens();
        this.http = buildHttpClient(properties.getTimeoutSeconds());
        log.info("AnthropicLlmClient ready (model={}, timeoutSeconds={})",
                model, properties.getTimeoutSeconds());
    }

    @Override
    public String complete(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        JsonNode response;
        try {
            response = http.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LlmCallException(
                    "Anthropic API call failed with status " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new LlmCallException("Anthropic API call failed: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new LlmCallException("Anthropic returned an empty response body");
        }
        JsonNode text = response.path("content").path(0).path("text");
        if (text.isMissingNode() || !text.isTextual()) {
            throw new LlmCallException("Anthropic response did not contain content[0].text");
        }
        return text.asText();
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    private static RestClient buildHttpClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }
}
