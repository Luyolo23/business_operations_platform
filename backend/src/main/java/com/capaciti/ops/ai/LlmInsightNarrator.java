package com.capaciti.ops.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "llm")
public class LlmInsightNarrator implements InsightNarrator {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightNarrator.class);
    private final LlmClient llm;

    public LlmInsightNarrator(LlmClient llm) { this.llm = llm; }

    @Override
    public String narrate(String facts) {
        try {
            return llm.chat("You are a business analyst. Rewrite the facts as a concise executive summary of "
                    + "at most 120 words. Use ONLY the facts provided; do not invent numbers.", facts).trim();
        } catch (Exception e) {
            log.warn("LLM narration failed ({}), returning raw facts", e.toString());
            return facts;
        }
    }
}
