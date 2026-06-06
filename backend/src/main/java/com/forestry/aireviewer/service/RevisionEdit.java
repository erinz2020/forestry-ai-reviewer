package com.forestry.aireviewer.service;

public record RevisionEdit(
        String author,
        String originalText,
        String insertedText,
        String location
) {
    public Kind kind() {
        boolean hasDel = originalText != null && !originalText.isEmpty();
        boolean hasIns = insertedText != null && !insertedText.isEmpty();
        if (hasDel && hasIns) return Kind.REPLACE;
        if (hasIns) return Kind.INSERT;
        return Kind.DELETE;
    }

    public enum Kind { REPLACE, INSERT, DELETE }
}
