package com.capaciti.ops.ai;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("openai/gpt-oss-120b") String model,
        @DefaultValue("20") int timeoutSeconds) {

    public AiProperties {
        provider = provider == null ? "" : provider.trim().toLowerCase();
        if (!provider.equals("mock") && !provider.equals("llm")) {
            throw new IllegalArgumentException("app.ai.provider must be 'mock' or 'llm', was '" + provider + "'");
        }

        baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("app.ai.base-url is not a valid URI: '" + baseUrl + "'", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "app.ai.base-url must be an absolute http(s) URL, was '" + baseUrl + "'");
        }

        model = model == null ? "" : model.trim();
        if (model.isEmpty()) {
            throw new IllegalArgumentException("app.ai.model must not be blank");
        }

        if (timeoutSeconds < 1 || timeoutSeconds > 120) {
            throw new IllegalArgumentException(
                    "app.ai.timeout-seconds must be between 1 and 120, was " + timeoutSeconds);
        }

        apiKey = apiKey == null ? "" : apiKey.trim();
        for (int i = 0; i < apiKey.length(); i++) {
            char c = apiKey.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                    || Character.isWhitespace(c)) {
                throw new IllegalArgumentException("app.ai.api-key contains an illegal character");
            }
        }
    }
}
