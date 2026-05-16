package com.forestry.aireviewer.client;

/**
 * Raised by {@link LlmClient} implementations when the upstream LLM call
 * fails (network error, non-2xx response, empty body).
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
