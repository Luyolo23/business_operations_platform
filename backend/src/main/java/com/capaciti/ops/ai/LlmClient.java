package com.capaciti.ops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal client for any OpenAI-compatible chat-completions API (Groq, OpenAI, Together, Ollama, ...).
 * Only created when app.ai.provider=llm.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "llm")
public class LlmClient {

    /** A hostile or broken provider must not be able to make us buffer an unbounded response. */
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public LlmClient(AiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        // Validate API key presence when using LLM provider
        if ("llm".equalsIgnoreCase(props.provider()) && (props.apiKey() == null || props.apiKey().isBlank())) {
            throw new IllegalStateException("API key must be provided for LLM provider. Set 'app.ai.api-key' in application.yml or via environment variable.");
        }
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.timeoutSeconds())).build();
    }

    public String model() { return props.model(); }

    public String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            response.body().close();
            throw new IOException("LLM call failed with HTTP " + response.statusCode());
        }
        byte[] bytes;
        try (InputStream in = response.body()) {
            bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
        }
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("LLM response exceeded " + MAX_RESPONSE_BYTES + " bytes");
        }
        JsonNode root = mapper.readTree(bytes);
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
