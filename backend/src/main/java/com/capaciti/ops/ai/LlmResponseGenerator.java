package com.capaciti.ops.ai;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "llm")
public class LlmResponseGenerator implements ResponseGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseGenerator.class);

    private final LlmClient llm;
    private final TemplateResponseGenerator fallback;

    public LlmResponseGenerator(LlmClient llm, TemplateResponseGenerator fallback) {
        this.llm = llm;
        this.fallback = fallback;
    }

    @Override
    public GeneratedResponse generate(Ticket t, Tone tone, String templateBody) {
        String system = """
                You write replies to internal business support tickets on behalf of the support team.
                Rules: write in a %s tone; address the requester by first name only; keep it under 120 words;
                never promise specific outcomes or deadlines; never include personal, financial or ID data;
                never blame the requester; use neutral, inclusive language. Output only the message body.
                """.formatted(tone.name().toLowerCase());
        String user = """
                Ticket #%d | category %s | priority %s
                Requester first name: %s
                Title: %s
                Description: %s

                Reference template (guide only):
                %s
                """.formatted(t.getId(), t.getCategory(), t.getPriority(),
                TemplateResponseGenerator.firstName(t.getRequester().getFullName()),
                t.getTitle(), t.getDescription(), templateBody);
        try {
            String text = llm.chat(system, user).trim();
            if (text.isBlank()) throw new IllegalStateException("empty LLM response");
            return new GeneratedResponse(text, "llm:" + llm.model(),
                    "Drafted by " + llm.model() + " using the ticket title/description and the "
                            + t.getCategory() + "/" + tone + " template as a guide. Review before sending.");
        } catch (Exception e) {
            log.warn("LLM response generation failed ({}), using template fallback", e.toString());
            return fallback.generate(t, tone, templateBody);
        }
    }
}
