package com.forestry.aireviewer.client;

/**
 * Thin abstraction over a remote LLM. One bean is registered per
 * {@code app.llm.provider} value.
 *
 * <p>Implementations are responsible for HTTP only — authentication, request
 * shaping, response unwrapping. Prompt rendering and response validation live
 * in {@code LLMReviewService}.</p>
 */
public interface LlmClient {

    /**
     * Sends {@code prompt} to the configured model and returns the raw text
     * the model produced. Throws {@link LlmCallException} on transport or
     * upstream failure.
     */
    String complete(String prompt);

    /** Provider identifier (e.g. "anthropic", "openai"). For logging only. */
    String providerName();
}
