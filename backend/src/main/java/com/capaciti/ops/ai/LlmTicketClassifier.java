package com.capaciti.ops.ai;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "llm")
public class LlmTicketClassifier implements TicketClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmTicketClassifier.class);

    private static final String SYSTEM = """
            You classify internal business support requests.
            Reply with ONLY a JSON object, no prose, in this exact shape:
            {"category":"HR|IT|FINANCE|OPERATIONS","priority":"LOW|MEDIUM|HIGH|URGENT","confidence":0.0-1.0,"rationale":"one short sentence"}
            """;

    private final LlmClient llm;
    private final KeywordTicketClassifier fallback;
    private final ObjectMapper mapper;

    public LlmTicketClassifier(LlmClient llm, KeywordTicketClassifier fallback, ObjectMapper mapper) {
        this.llm = llm;
        this.fallback = fallback;
        this.mapper = mapper;
    }

    @Override
    public ClassificationResult classify(String title, String description) {
        try {
            String raw = llm.chat(SYSTEM, "Title: " + title + "\nDescription: " + description);
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            JsonNode json = mapper.readTree(raw.substring(start, end + 1));
            Category category = Category.valueOf(json.path("category").asText().trim().toUpperCase());
            Priority priority = Priority.valueOf(json.path("priority").asText().trim().toUpperCase());
            double confidence = Math.max(0, Math.min(1, json.path("confidence").asDouble(0.7)));
            return new ClassificationResult(category, priority, confidence, "LLM:" + llm.model(),
                    json.path("rationale").asText(""));
        } catch (Exception e) {
            log.warn("LLM classification failed ({}), using keyword fallback", e.toString());
            ClassificationResult r = fallback.classify(title, description);
            return new ClassificationResult(r.category(), r.priority(), r.confidence(), "KEYWORD_FALLBACK",
                    r.rationale());
        }
    }
}
