package com.forestry.aireviewer.client;

import com.forestry.aireviewer.config.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicLlmClientTest {

    @Test
    @DisplayName("constructor rejects a null API key")
    void nullApiKey_throws() {
        assertThatThrownBy(() -> new AnthropicLlmClient(props(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    @DisplayName("constructor rejects a blank API key")
    void blankApiKey_throws() {
        assertThatThrownBy(() -> new AnthropicLlmClient(props(), "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    @DisplayName("providerName reports 'anthropic' once constructed with a key")
    void providerName_isAnthropic() {
        AnthropicLlmClient client = new AnthropicLlmClient(props(), "sk-test");

        assertThat(client.providerName()).isEqualTo("anthropic");
    }

    private LlmProperties props() {
        LlmProperties p = new LlmProperties();
        p.setProvider("anthropic");
        p.setModel("claude-sonnet-4-6");
        p.setTimeoutSeconds(60);
        p.setMaxOutputTokens(2048);
        return p;
    }
}
