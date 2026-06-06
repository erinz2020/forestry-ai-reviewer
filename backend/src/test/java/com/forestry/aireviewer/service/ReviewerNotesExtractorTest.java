package com.forestry.aireviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewerNotesExtractorTest {

    private final ReviewerNotesExtractor extractor = new ReviewerNotesExtractor();

    @Test
    @DisplayName("Chinese ordinal items (一、 二、) are split into separate notes")
    void chineseOrdinalItems_splitCorrectly() {
        String text = """
                关于《坳上林场森林经营方案》的审核意见

                一、关于第 5.2 节生物多样性章节
                建议补充调查时段说明，并附野外调查记录。

                二、关于第 6.1 节经营目标
                目标值缺少基线参考，应注明 2020 年现状。

                三、其它
                请校对全文错别字。
                """;

        List<NoteItem> items = extractor.parseItems(text);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).text()).startsWith("一、");
        assertThat(items.get(0).text()).contains("调查时段");
        assertThat(items.get(0).sectionReference()).isEqualTo("第 5.2 节");
        assertThat(items.get(1).sectionReference()).isEqualTo("第 6.1 节");
        assertThat(items.get(2).sectionReference()).isNull();
    }

    @Test
    @DisplayName("parenthesised sub-items (（一）) are split as their own notes")
    void parenthesisedSubItems_splitCorrectly() {
        String text = """
                一、总体意见
                修改方向正确，细节仍需打磨。

                （一）数据来源
                请补充原始数据出处。

                （二）图表
                图表编号不连续。
                """;

        List<NoteItem> items = extractor.parseItems(text);

        assertThat(items).hasSize(3);
        assertThat(items.get(1).text()).startsWith("（一）");
        assertThat(items.get(2).text()).startsWith("（二）");
    }

    @Test
    @DisplayName("Arabic-numbered items (1. 2.) are split")
    void arabicNumberedItems_splitCorrectly() {
        String text = """
                1. 修改方向正确。
                2. 数据有缺。
                3. 图表编号不连续。
                """;

        List<NoteItem> items = extractor.parseItems(text);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).text()).isEqualTo("1. 修改方向正确。");
    }

    @Test
    @DisplayName("when no numbered items found, fall back to blank-line paragraphs")
    void noNumberedItems_fallbackToParagraphs() {
        String text = """
                总体上方向是对的。

                但是数据需要补充。

                图表需要重新排版。
                """;

        List<NoteItem> items = extractor.parseItems(text);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).text()).contains("总体上");
        assertThat(items.get(2).text()).contains("图表");
    }

    @Test
    @DisplayName("section reference '5.2节' (no 第 prefix) is captured")
    void sectionReference_withoutDiPrefix_captured() {
        String text = "1. 关于 5.2 节内容缺失，需补充。";
        List<NoteItem> items = extractor.parseItems(text);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).sectionReference()).contains("5.2");
    }

    @Test
    @DisplayName("Chinese-numeral section reference (第五章) is captured")
    void sectionReference_chineseNumeral_captured() {
        String text = "1. 第五章缺少结论。";
        List<NoteItem> items = extractor.parseItems(text);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).sectionReference()).isEqualTo("第五章");
    }

    @Test
    @DisplayName("empty / blank text returns no items")
    void blankText_returnsEmpty() {
        assertThat(extractor.parseItems("")).isEmpty();
        assertThat(extractor.parseItems("   \n  \n  ")).isEmpty();
    }

    @Test
    @DisplayName("第N条 / 第N节 numbered headings start new items")
    void numberedHeadings_startNewItems() {
        String text = """
                第一条 数据要求
                所有图表必须有出处。

                第二条 引用规范
                参考文献请使用国标格式。
                """;

        List<NoteItem> items = extractor.parseItems(text);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).text()).startsWith("第一条");
        assertThat(items.get(1).text()).startsWith("第二条");
    }
}
