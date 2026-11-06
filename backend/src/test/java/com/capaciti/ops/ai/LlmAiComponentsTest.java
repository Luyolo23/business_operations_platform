package com.capaciti.ops.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmAiComponentsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private volatile int status = 200;
    private volatile String modelSays = "";
    private volatile String seenAuth;
    private volatile String seenBody;

    @BeforeEach
    void startFakeGroq() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", ex -> {
            seenAuth = ex.getRequestHeaders().getFirst("Authorization");
            seenBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = mapper.writeValueAsBytes(
                    Map.of("choices", List.of(Map.of("message", Map.of("content", modelSays)))));
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
    }

    @AfterEach
    void stopFakeGroq() { server.stop(0); }

    private LlmClient client() {
        var props = new AiProperties("llm", "http://localhost:" + server.getAddress().getPort(),
                "test-key", "test-model", 5);
        return new LlmClient(props, mapper);
    }

    private LlmTicketClassifier classifier() {
        return new LlmTicketClassifier(client(), new KeywordTicketClassifier(), mapper);
    }

    private LlmResponseGenerator generator() {
        return new LlmResponseGenerator(client(), new TemplateResponseGenerator());
    }

    private static Ticket ticket() {
        User u = new User();
        u.setFullName("Emma Employee");
        Ticket t = new Ticket();
        t.setId(7L);
        t.setTitle("VPN drops");
        t.setDescription("The VPN disconnects every few minutes");
        t.setCategory(Category.IT);
        t.setPriority(Priority.HIGH);
        t.setRequester(u);
        return t;
    }

    @Test
    void classifierReadsJsonEvenWhenTheModelAddsExtraWords() {
        modelSays = "Sure! ```json\n{\"category\":\"HR\",\"priority\":\"LOW\",\"confidence\":0.91,\"rationale\":\"leave\"}\n```";
        var r = classifier().classify("Time off", "I want leave in December");
        assertEquals(Category.HR, r.category());
        assertEquals(Priority.LOW, r.priority());
        assertEquals(0.91, r.confidence(), 1e-9);
        assertEquals("LLM:test-model", r.source());
    }

    @Test
    void classifierSendsTheKeyTheModelNameAndTheTicketText() {
        modelSays = "{\"category\":\"IT\",\"priority\":\"HIGH\",\"confidence\":0.8,\"rationale\":\"vpn\"}";
        classifier().classify("VPN not working", "cannot connect");
        assertEquals("Bearer test-key", seenAuth);
        assertTrue(seenBody.contains("test-model"));
        assertTrue(seenBody.contains("VPN not working"));
    }

    @Test
    void classifierFallsBackToKeywordsWhenTheApiFails() {
        status = 500;
        var r = classifier().classify("VPN not working", "I cannot connect to the VPN");
        assertEquals("KEYWORD_FALLBACK", r.source());
        assertEquals(Category.IT, r.category());
    }

    @Test
    void classifierFallsBackWhenTheModelInventsACategory() {
        modelSays = "{\"category\":\"LEGAL\",\"priority\":\"LOW\",\"confidence\":0.9,\"rationale\":\"x\"}";
        assertEquals("KEYWORD_FALLBACK", classifier().classify("VPN", "vpn down").source());
    }

    @Test
    void replyGeneratorUsesTheModelText() {
        modelSays = "Hi Emma, we are looking into your VPN issue.";
        var g = generator().generate(ticket(), Tone.FRIENDLY, "Hi {{requesterName}}");
        assertEquals("Hi Emma, we are looking into your VPN issue.", g.body());
        assertEquals("llm:test-model", g.generatedBy());
    }

    @Test
    void replyGeneratorFallsBackToTheTemplate() {
        status = 500;
        var g = generator().generate(ticket(), Tone.FORMAL, "Hi {{requesterName}}, ticket {{ticketId}}");
        assertEquals("Hi Emma, ticket 7", g.body());
        assertEquals("template", g.generatedBy());
    }
}