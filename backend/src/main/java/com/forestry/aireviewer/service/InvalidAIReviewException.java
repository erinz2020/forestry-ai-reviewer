package com.forestry.aireviewer.service;

/**
 * Thrown when an {@link com.forestry.aireviewer.dto.AIReviewResponse} fails
 * validation. No findings are persisted when this is raised.
 */
public class InvalidAIReviewException extends RuntimeException {

    public InvalidAIReviewException(String message) {
        super(message);
    }
}
