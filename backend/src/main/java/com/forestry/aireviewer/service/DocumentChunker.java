package com.forestry.aireviewer.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Greedy, structure-aware text chunker. Splits the extracted document text
 * into bounded slices, preferring paragraph and section boundaries over
 * arbitrary character breaks.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Chunks target {@link #MAX_CHUNK_SIZE} characters and never exceed it.</li>
 *   <li>A paragraph that starts with a section header (e.g. "Section 3.2",
 *       "Chapter 4", "第三章") forces a new chunk as long as the current chunk
 *       already has at least {@link #MIN_CHUNK_SIZE} characters.</li>
 *   <li>A single paragraph larger than the max is hard-split at the max size.</li>
 * </ul>
 *
 * <p>{@code startOffset} and {@code endOffset} on each chunk are character
 * offsets into the input text, so the original source can always be located.</p>
 */
@Component
public class DocumentChunker {

    static final int MAX_CHUNK_SIZE = 4000;
    static final int MIN_CHUNK_SIZE = 500;

    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n+");

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^\\s*(?:" +
                    "Section\\s+\\d+(?:\\.\\d+)*\\b|" +
                    "Chapter\\s+\\d+\\b|" +
                    "Appendix\\s+[A-Z]\\b|" +
                    "\\d+(?:\\.\\d+)+\\s+\\S|" +
                    "第[一二三四五六七八九十百千万零\\d]+[章节]" +
                    ")");

    public record ChunkData(int chunkIndex, String content, int startOffset, int endOffset) {}

    public List<ChunkData> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<int[]> paragraphs = splitParagraphs(text);
        List<ChunkData> result = new ArrayList<>();
        int chunkIdx = 0;
        int chunkStart = -1;
        int chunkEnd = -1;

        for (int[] p : paragraphs) {
            int pStart = p[0];
            int pEnd = p[1];
            int pLen = pEnd - pStart;

            boolean currentHasContent = chunkStart >= 0;
            int currentLen = currentHasContent ? chunkEnd - chunkStart : 0;
            boolean wouldExceedMax = currentHasContent && currentLen + pLen > MAX_CHUNK_SIZE;
            boolean isHeader = isSectionHeader(text, pStart, pEnd);
            boolean forceBreakOnHeader = currentHasContent && isHeader && currentLen >= MIN_CHUNK_SIZE;

            if (wouldExceedMax || forceBreakOnHeader) {
                result.add(new ChunkData(chunkIdx++, text.substring(chunkStart, chunkEnd), chunkStart, chunkEnd));
                chunkStart = -1;
            }

            if (pLen > MAX_CHUNK_SIZE) {
                if (chunkStart >= 0) {
                    result.add(new ChunkData(chunkIdx++, text.substring(chunkStart, chunkEnd), chunkStart, chunkEnd));
                    chunkStart = -1;
                }
                for (int i = pStart; i < pEnd; i += MAX_CHUNK_SIZE) {
                    int end = Math.min(i + MAX_CHUNK_SIZE, pEnd);
                    result.add(new ChunkData(chunkIdx++, text.substring(i, end), i, end));
                }
                continue;
            }

            if (chunkStart < 0) {
                chunkStart = pStart;
            }
            chunkEnd = pEnd;
        }

        if (chunkStart >= 0) {
            result.add(new ChunkData(chunkIdx, text.substring(chunkStart, chunkEnd), chunkStart, chunkEnd));
        }

        return result;
    }

    private List<int[]> splitParagraphs(String text) {
        List<int[]> paragraphs = new ArrayList<>();
        Matcher m = PARAGRAPH_SPLIT.matcher(text);
        int cursor = 0;
        while (m.find()) {
            if (m.start() > cursor) {
                paragraphs.add(new int[]{cursor, m.start()});
            }
            cursor = m.end();
        }
        if (cursor < text.length()) {
            paragraphs.add(new int[]{cursor, text.length()});
        }
        return paragraphs;
    }

    private boolean isSectionHeader(String text, int start, int end) {
        int newline = text.indexOf('\n', start);
        int lineEnd = (newline == -1 || newline > end) ? end : newline;
        String firstLine = text.substring(start, lineEnd);
        return SECTION_HEADER.matcher(firstLine).find();
    }
}
