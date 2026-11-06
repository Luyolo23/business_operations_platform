package com.capaciti.ops.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("openai/gpt-oss-120b") String model,
        @DefaultValue("20") int timeoutSeconds) {
}
