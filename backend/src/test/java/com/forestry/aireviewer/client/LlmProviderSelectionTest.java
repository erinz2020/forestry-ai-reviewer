package com.forestry.aireviewer.client;

import com.forestry.aireviewer.service.LLMReviewService;
import com.forestry.aireviewer.service.MockReviewService;
import com.forestry.aireviewer.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderSelectionTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "app.review.provider=mock",
            "app.llm.provider=anthropic"
    })
    @DisplayName("provider=mock: only MockReviewService is wired; no LLM client beans loaded")
    class MockProviderContext {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private ReviewService reviewService;

        @Test
        void mockProviderIsActive() {
            assertThat(reviewService).isInstanceOf(MockReviewService.class);
            assertThatThrownBy(() -> context.getBean(LLMReviewService.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
            assertThatThrownBy(() -> context.getBean(AnthropicLlmClient.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "app.review.provider=llm",
            "app.llm.provider=anthropic",
            "ANTHROPIC_API_KEY=fake-key-for-context-load"
    })
    @DisplayName("provider=llm, anthropic: AnthropicLlmClient + LLMReviewService are wired, OpenAi is not")
    class LlmAnthropicContext {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private ReviewService reviewService;

        @Autowired
        private LlmClient llmClient;

        @Test
        void anthropicProviderIsActive() {
            assertThat(reviewService).isInstanceOf(LLMReviewService.class);
            assertThat(llmClient).isInstanceOf(AnthropicLlmClient.class);
            assertThat(llmClient.providerName()).isEqualTo("anthropic");
            assertThatThrownBy(() -> context.getBean(OpenAiLlmClient.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "app.review.provider=llm",
            "app.llm.provider=openai",
            "OPENAI_API_KEY=fake-key-for-context-load"
    })
    @DisplayName("provider=llm, openai: OpenAiLlmClient + LLMReviewService are wired, Anthropic is not")
    class LlmOpenAiContext {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private LlmClient llmClient;

        @Test
        void openAiProviderIsActive() {
            assertThat(llmClient).isInstanceOf(OpenAiLlmClient.class);
            assertThat(llmClient.providerName()).isEqualTo("openai");
            assertThatThrownBy(() -> context.getBean(AnthropicLlmClient.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }
}
