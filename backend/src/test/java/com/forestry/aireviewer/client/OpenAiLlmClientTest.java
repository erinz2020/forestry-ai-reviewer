package com.forestry.aireviewer.client;

import com.forestry.aireviewer.config.LlmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiLlmClientTest {

    @Test
    @DisplayName("constructor rejects a null API key")
    void nullApiKey_throws() {
        assertThatThrownBy(() -> new OpenAiLlmClient(props(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("constructor rejects a blank API key")
    void blankApiKey_throws() {
        assertThatThrownBy(() -> new OpenAiLlmClient(props(), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("providerName reports 'openai' once constructed with a key")
    void providerName_isOpenAi() {
        OpenAiLlmClient client = new OpenAiLlmClient(props(), "sk-test");

        assertThat(client.providerName()).isEqualTo("openai");
    }

    private LlmProperties props() {
        LlmProperties p = new LlmProperties();
        p.setProvider("openai");
        p.setModel("gpt-4o-mini");
        p.setTimeoutSeconds(60);
        p.setMaxOutputTokens(2048);
        return p;
    }
}
