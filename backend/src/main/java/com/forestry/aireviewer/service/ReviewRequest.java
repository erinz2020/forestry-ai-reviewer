package com.forestry.aireviewer.service;

import java.util.UUID;

/**
 * Per-chunk request handed to a {@link ReviewService} provider.
 *
 * <p>The chunk metadata exists so the LLM provider can tell the model
 * "this is chunk {@code chunkIndex} of {@code totalChunks}", which helps the
 * model avoid hallucinating cross-chunk evidence. The mock provider may
 * ignore the metadata.</p>
 */
public record ReviewRequest(
        UUID documentId,
        String documentTitle,
        String chunkContent,
        int chunkIndex,
        int totalChunks
) {
}
