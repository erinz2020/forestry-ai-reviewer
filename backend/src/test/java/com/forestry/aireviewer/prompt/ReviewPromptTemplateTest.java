package com.forestry.aireviewer.prompt;

import com.forestry.aireviewer.service.ReviewRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPromptTemplateTest {

    private final ReviewPromptTemplate template = new ReviewPromptTemplate();

    @Test
    @DisplayName("render fills the document title, chunk index, total chunks, and chunk content")
    void render_replacesAllPlaceholders() {
        ReviewRequest request = new ReviewRequest(
                UUID.randomUUID(),
                "feasibility-report.pdf",
                "This is the chunk body about biodiversity.",
                2,
                5);

        String prompt = template.render(request);

        assertThat(prompt).contains("feasibility-report.pdf");
        assertThat(prompt).contains("3 of 5");
        assertThat(prompt).contains("This is the chunk body about biodiversity.");
        assertThat(prompt).doesNotContain("{{documentTitle}}");
        assertThat(prompt).doesNotContain("{{chunkIndex}}");
        assertThat(prompt).doesNotContain("{{totalChunks}}");
        assertThat(prompt).doesNotContain("{{chunkContent}}");
    }

    @Test
    @DisplayName("render falls back to 'untitled' when the document title is null or blank")
    void render_blankTitle_fallsBack() {
        ReviewRequest req1 = new ReviewRequest(UUID.randomUUID(), null, "body", 0, 1);
        ReviewRequest req2 = new ReviewRequest(UUID.randomUUID(), "   ", "body", 0, 1);

        assertThat(template.render(req1)).contains("Document title:\nuntitled");
        assertThat(template.render(req2)).contains("Document title:\nuntitled");
    }

    @Test
    @DisplayName("render rejects null requests and blank chunk content")
    void render_invalidInput_throws() {
        assertThatThrownBy(() -> template.render(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");

        ReviewRequest blank = new ReviewRequest(UUID.randomUUID(), "title", "   ", 0, 1);
        assertThatThrownBy(() -> template.render(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkContent");
    }

    @Test
    @DisplayName("template names all eight review categories in snake_case")
    void template_listsAllCategories() {
        String raw = template.rawTemplate();
        assertThat(raw)
                .contains("internal_contradiction")
                .contains("missing_evidence")
                .contains("biodiversity_risk")
                .contains("environmental_impact")
                .contains("unsupported_conclusion")
                .contains("vague_language")
                .contains("weak_mitigation")
                .contains("possible_regulatory_concern");
    }

    @Test
    @DisplayName("template states the non-negotiable behavioural rules")
    void template_containsCoreRules() {
        String raw = template.rawTemplate();
        assertThat(raw)
                .contains("Do NOT approve or reject")
                .contains("Do NOT invent facts")
                .contains("Do NOT invent regulations")
                .contains("grounded in THIS chunk")
                .contains("LOW confidence")
                .contains("POSSIBLE concern")
                .contains("Do NOT claim a law or regulation has been violated")
                .contains("Output JSON ONLY")
                .contains("No markdown");
    }

    @Test
    @DisplayName("template specifies severity and confidence constraints")
    void template_containsSchemaConstraints() {
        String raw = template.rawTemplate();
        assertThat(raw)
                .contains("\"low\"")
                .contains("\"medium\"")
                .contains("\"high\"")
                .contains("between 0.0 and 1.0");
    }

    @Test
    @DisplayName("template includes the JSON output schema and the empty-result shape")
    void template_containsOutputSchema() {
        String raw = template.rawTemplate();
        assertThat(raw)
                .contains("\"findings\"")
                .contains("\"category\"")
                .contains("\"severity\"")
                .contains("\"confidence\"")
                .contains("\"section\"")
                .contains("\"finding\"")
                .contains("\"evidence\"")
                .contains("\"recommendation\"")
                .contains("\"sourceReferences\"")
                .contains("{\"findings\": []}");
    }

    @Test
    @DisplayName("render preserves chunk content containing brace-like characters")
    void render_braceLikeContent_isLiteralSubstitution() {
        String body = "Section 1: { not a placeholder } and {{also not}}.";
        ReviewRequest req = new ReviewRequest(UUID.randomUUID(), "doc.pdf", body, 0, 1);

        assertThat(template.render(req)).contains(body);
    }
}
