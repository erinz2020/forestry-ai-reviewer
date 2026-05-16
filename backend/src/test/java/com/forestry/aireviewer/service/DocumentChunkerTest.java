package com.forestry.aireviewer.service;

import com.forestry.aireviewer.service.DocumentChunker.ChunkData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    @DisplayName("blank or null input produces no chunks")
    void emptyInput_returnsEmpty() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    @DisplayName("a short single-paragraph document produces a single chunk covering the whole text")
    void shortDocument_singleChunk() {
        String text = "A short forestry report paragraph.";

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        ChunkData only = chunks.get(0);
        assertThat(only.chunkIndex()).isEqualTo(0);
        assertThat(only.content()).isEqualTo(text);
        assertThat(only.startOffset()).isEqualTo(0);
        assertThat(only.endOffset()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("multiple short paragraphs are greedily packed into one chunk under the limit")
    void multipleShortParagraphs_packedTogether() {
        String text = "Para one introducing the project area.\n\n"
                + "Para two describing baseline data.\n\n"
                + "Para three covering mitigation measures.";

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("Para one").contains("Para two").contains("Para three");
    }

    @Test
    @DisplayName("a section header forces a new chunk once the running chunk has enough content")
    void sectionHeader_forcesBreak() {
        String first = repeat("Paragraph body content covering project description. ", 30);
        String text = first + "\n\nSection 3.2 Biodiversity Assessment\n\n"
                + repeat("Biodiversity assessment paragraph body. ", 30);

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).content()).doesNotContain("Section 3.2 Biodiversity Assessment");
        boolean secondStartsWithHeader = chunks.get(1).content().trim().startsWith("Section 3.2");
        assertThat(secondStartsWithHeader).isTrue();
    }

    @Test
    @DisplayName("a single paragraph larger than the max chunk size is hard-split")
    void oversizedParagraph_isHardSplit() {
        String huge = repeat("X", DocumentChunker.MAX_CHUNK_SIZE * 2 + 50);

        List<ChunkData> chunks = chunker.chunk(huge);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (ChunkData c : chunks) {
            assertThat(c.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_SIZE);
        }
        int totalLen = chunks.stream().mapToInt(c -> c.content().length()).sum();
        assertThat(totalLen).isEqualTo(huge.length());
    }

    @Test
    @DisplayName("chunk offsets are sequential, non-overlapping, and cover all paragraph content")
    void offsets_areConsistent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Paragraph ").append(i).append(" body content here. ").append(repeat("word ", 30));
            sb.append("\n\n");
        }
        String text = sb.toString();

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).isNotEmpty();
        int previousEnd = 0;
        for (int i = 0; i < chunks.size(); i++) {
            ChunkData c = chunks.get(i);
            assertThat(c.chunkIndex()).isEqualTo(i);
            assertThat(c.startOffset()).isGreaterThanOrEqualTo(previousEnd);
            assertThat(c.endOffset()).isGreaterThan(c.startOffset());
            assertThat(c.endOffset()).isLessThanOrEqualTo(text.length());
            assertThat(c.content()).isEqualTo(text.substring(c.startOffset(), c.endOffset()));
            previousEnd = c.endOffset();
        }
    }

    @Test
    @DisplayName("no chunk exceeds the max chunk size")
    void noChunkExceedsMaxSize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("Paragraph ").append(i).append(" ").append(repeat("body word ", 80));
            sb.append("\n\n");
        }

        List<ChunkData> chunks = chunker.chunk(sb.toString());

        assertThat(chunks).isNotEmpty();
        for (ChunkData c : chunks) {
            assertThat(c.content().length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_SIZE);
        }
    }

    @Test
    @DisplayName("a section header at the very start of the document does not produce an empty leading chunk")
    void leadingSectionHeader_noEmptyChunk() {
        String text = "Section 1 Introduction\n\nIntroductory paragraph body.";

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).content().trim()).startsWith("Section 1 Introduction");
    }

    @Test
    @DisplayName("Chinese section headers (第N章) also force a break")
    void chineseSectionHeader_forcesBreak() {
        String first = repeat("项目背景段落内容。", 80);
        String text = first + "\n\n第三章 生物多样性评估\n\n" + repeat("评估段落内容。", 60);

        List<ChunkData> chunks = chunker.chunk(text);

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        boolean anyStartsWithSection = chunks.stream()
                .anyMatch(c -> c.content().trim().startsWith("第三章"));
        assertThat(anyStartsWithSection).isTrue();
    }

    private static String repeat(String s, int times) {
        return String.valueOf(s).repeat(Math.max(0, times));
    }
}
