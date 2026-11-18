package com.capaciti.ops.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
@Tag("live")
@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
class GroqLiveTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    private LlmClient client() {
        var props = new AiProperties("llm", env("AI_BASE_URL", "https://api.groq.com/openai/v1"),
                System.getenv("AI_API_KEY"), env("AI_MODEL", "openai/gpt-oss-120b"), 30);
        return new LlmClient(props, mapper);
    }

    @Test
    void groqUnderstandsWhatKeywordsCannot() {
        String title = "Time off in December";
        String text = "I need to take time off in December but my manager has not signed off";
        var keyword = new KeywordTicketClassifier().classify(title, text);
        var ai = new LlmTicketClassifier(client(), new KeywordTicketClassifier(), mapper).classify(title, text);

        System.out.println("keyword: " + keyword);
        System.out.println("groq:    " + ai);
        assertTrue(ai.source().startsWith("LLM:"), "Groq was not used, source was " + ai.source());
        assertEquals(Category.HR, ai.category());
        assertNotEquals(Category.HR, keyword.category());
    }

    @Test
    void groqDraftsAReply() {
        User u = new User();
        u.setFullName("Emma Employee");
        Ticket t = new Ticket();
        t.setId(1L);
        t.setTitle("VPN drops");
        t.setDescription("The VPN disconnects every few minutes");
        t.setCategory(Category.IT);
        t.setPriority(Priority.HIGH);
        t.setRequester(u);

        var reply = new LlmResponseGenerator(client(), new TemplateResponseGenerator())
                .generate(t, Tone.FRIENDLY, "Hi {{requesterName}}, we are on it.");
        System.out.println(reply.body());
        assertTrue(reply.generatedBy().startsWith("llm:"), "Groq was not used, got " + reply.generatedBy());
    }
}