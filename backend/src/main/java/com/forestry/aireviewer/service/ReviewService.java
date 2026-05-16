package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewResponse;

/**
 * Swappable AI review provider. Implementations are selected via
 * the {@code app.review.provider} configuration property.
 *
 * <p>Providers operate on one document chunk at a time and return a strict
 * {@link AIReviewResponse} DTO. The orchestrator is responsible for
 * iterating chunks, validating responses, deduplicating, and persisting.</p>
 */
public interface ReviewService {

    AIReviewResponse review(ReviewRequest request);
}
