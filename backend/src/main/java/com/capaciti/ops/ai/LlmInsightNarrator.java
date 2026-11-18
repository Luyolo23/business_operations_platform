package com.capaciti.ops.ai;

import com.capaciti.ops.common.TextSafety;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final int MAX_NARRATION_CHARS = 4000;

    private final LlmClient llm;

    public LlmInsightNarrator(LlmClient llm) { this.llm = llm; }

    @Override
    public String narrate(String facts) {
        if (facts == null || facts.isBlank()) return "";
        try {
            String user = "Rewrite these verified facts as a concise executive summary of at most 120 words. "
                    + "Use ONLY the facts provided; do not invent numbers.\n" + PromptSafety.wrap(facts);
            String text = TextSafety.cleanReply(llm.chat(
                    "You are a business analyst writing for management. Be concise and factual.", user));
            if (text.isBlank() || text.length() > MAX_NARRATION_CHARS) {
                log.warn("LLM narration was blank or too long, returning raw facts");
                return facts;
            }
            if (!numbersAreGrounded(text, facts)) {
                log.warn("LLM narration invented a number that is not in the facts, returning raw facts");
                return facts;
            }
            return text;
        } catch (Exception e) {
            log.warn("LLM narration failed ({}), returning raw facts", e.toString());
            return facts;
        }
    }

    /** Every number in the summary must already appear somewhere in the supplied facts. */
    private static boolean numbersAreGrounded(String narration, String facts) {
        Set<String> allowed = numbers(facts);
        for (String value : numbers(narration)) {
            if (!allowed.contains(value)) return false;
        }
        return true;
    }

    private static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }
}
