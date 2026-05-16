package com.forestry.aireviewer.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import com.forestry.aireviewer.ForestryAiReviewerApplication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmMissingApiKeyTest {

    @Test
    @DisplayName("startup fails when provider=llm/anthropic and ANTHROPIC_API_KEY is missing")
    void anthropic_missingKey_failsStartup() {
        SpringApplication app = new SpringApplication(ForestryAiReviewerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> app.run(
                "--app.review.provider=llm",
                "--app.llm.provider=anthropic",
                "--ANTHROPIC_API_KEY=",
                "--OPENAI_API_KEY=",
                "--spring.datasource.url=jdbc:h2:mem:nokey-anthropic",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--upload.dir=./test-uploads",
                "--spring.flyway.enabled=false"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("ANTHROPIC_API_KEY");
    }

    @Test
    @DisplayName("startup fails when provider=llm/openai and OPENAI_API_KEY is missing")
    void openai_missingKey_failsStartup() {
        SpringApplication app = new SpringApplication(ForestryAiReviewerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> app.run(
                "--app.review.provider=llm",
                "--app.llm.provider=openai",
                "--ANTHROPIC_API_KEY=",
                "--OPENAI_API_KEY=",
                "--spring.datasource.url=jdbc:h2:mem:nokey-openai",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--upload.dir=./test-uploads",
                "--spring.flyway.enabled=false"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("OPENAI_API_KEY");
    }
}
