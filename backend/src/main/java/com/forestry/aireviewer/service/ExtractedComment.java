package com.forestry.aireviewer.service;

public record ExtractedComment(
        String text,
        String author,
        String referencedText,
        String approximateLocation
) {}
