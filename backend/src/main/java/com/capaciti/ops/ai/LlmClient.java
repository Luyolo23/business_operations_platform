package com.capaciti.ops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("LLM call failed with HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }
}
