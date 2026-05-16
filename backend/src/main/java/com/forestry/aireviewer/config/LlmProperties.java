package com.forestry.aireviewer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the real LLM-backed review provider. Bound from the
 * {@code app.llm.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

    /** "anthropic" or "openai". */
    private String provider = "anthropic";

    /** Provider-specific model identifier. */
    private String model = "claude-sonnet-4-6";

    /** Read timeout for the LLM HTTP call, in seconds. */
    private int timeoutSeconds = 60;

    /** Max tokens the model may return. */
    private int maxOutputTokens = 4096;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
}
