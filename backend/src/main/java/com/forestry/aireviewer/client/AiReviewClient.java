package com.forestry.aireviewer.client;

import com.forestry.aireviewer.model.Finding;

import java.util.List;
import java.util.UUID;

public interface AiReviewClient {

    List<Finding> review(String extractedText, String documentTitle, UUID documentId);
}
