package com.capaciti.ops;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.junit.jupiter.api.Tag;

// Forces mock AI so the test is deterministic even if AI_PROVIDER=llm is exported in your shell.
@SpringBootTest(properties = {"app.ai.provider=mock", "app.seed.enabled=true", "app.seed.demo-tickets=false"})
@AutoConfigureMockMvc
@Testcontainers          // was: disabledWithoutDocker=true — removed so Docker absence is an explicit failure, not a silent skip
@Tag("integration")      // exclude with: mvn test -DexcludedGroups=integration  (only when Docker is unavailable)
class TicketFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private JsonNode createTicket(String auth, String title, String description) throws Exception {
        String body = mvc.perform(post("/api/tickets").header("Authorization", auth).contentType(JSON)
                        .content(json.writeValueAsString(Map.of("title", title, "description", description))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode draftReply(String auth, long ticketId) throws Exception {
        String body = mvc.perform(post("/api/tickets/" + ticketId + "/responses").header("Authorization", auth)
                        .contentType(JSON).content("{\"tone\":\"FORMAL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    void ticketIsClassifiedRoutedAndPrivateToItsOwner() throws Exception {
        String employee = login("employee@ops.local");
        JsonNode t = createTicket(employee, "VPN not working", "I cannot connect to the VPN from my laptop");

        assertEquals("IT", t.get("category").asText());
        assertEquals("ASSIGNED", t.get("status").asText());
        assertEquals("Ian IT-Agent", t.get("assigneeName").asText());
        assertEquals("KEYWORD", t.get("classificationSource").asText());

        String reg = mvc.perform(post("/api/auth/register").contentType(JSON).content(json.writeValueAsString(
                        Map.of("fullName", "Other Person", "email", "other@ops.local", "password", "Passw0rd!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String other = "Bearer " + json.readTree(reg).get("token").asText();

        mvc.perform(get("/api/tickets/" + t.get("id").asLong()).header("Authorization", employee))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets/" + t.get("id").asLong()).header("Authorization", other))
                .andExpect(status().isForbidden());
    }

    @Test
    void safeReplyIsSentImmediately() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Printer offline", "The office printer shows an error")
                .get("id").asLong();
        String agent = login("agent.it@ops.local");

        JsonNode reply = draftReply(agent, id);
        assertEquals("APPROVED", reply.get("status").asText());
        assertEquals("NONE", reply.get("riskLevel").asText());

        mvc.perform(get("/api/tickets/" + id).header("Authorization", agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.firstResponseAt").value(notNullValue()));
    }

    @Test
    void riskyReplyIsHeldUntilAManagerApproves() throws Exception {
        // the ticket title is copied into the reply, so "guarantee" trips the risk checker
        JsonNode t = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me");
        long id = t.get("id").asLong();
        assertEquals("FINANCE", t.get("category").asText());

        JsonNode reply = draftReply(login("agent.finance@ops.local"), id);
        assertEquals("PENDING_APPROVAL", reply.get("status").asText());
        assertEquals("MEDIUM", reply.get("riskLevel").asText());
        long approvalId = reply.get("approvalId").asLong();

        String manager = login("manager@ops.local");
        mvc.perform(get("/api/tickets/" + id).header("Authorization", manager))
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"));

        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{\"approve\":true,\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/tickets/" + id).header("Authorization", manager))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    private JsonNode writeReply(String auth, long ticketId, String body) throws Exception {
        String response = mvc.perform(post("/api/tickets/" + ticketId + "/responses").header("Authorization", auth)
                        .contentType(JSON).content(json.writeValueAsString(Map.of("tone", "FORMAL", "body", body))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void agentCanWriteAManualReply() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Laptop request",
                "I need a new laptop for the design team").get("id").asLong();
        String agent = login("agent.it@ops.local");

        JsonNode reply = writeReply(agent, id, "We have ordered a laptop for you; it arrives on Monday.");

        assertEquals("APPROVED", reply.get("status").asText());
        assertEquals("manual", reply.get("generatedBy").asText());
        assertEquals("We have ordered a laptop for you; it arrives on Monday.", reply.get("body").asText());

        mvc.perform(get("/api/tickets/" + id).header("Authorization", agent))
                .andExpect(jsonPath("$.firstResponseAt").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void manualRepliesAreStillRiskScreened() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Refund question",
                "Can you tell me about my refund").get("id").asLong();

        JsonNode reply = writeReply(login("agent.finance@ops.local"), id,
                "We guarantee you will get your money back.");

        assertEquals("PENDING_APPROVAL", reply.get("status").asText());
        assertEquals("MEDIUM", reply.get("riskLevel").asText());
    }

    @Test
    void ticketCannotBeResolvedBeforeAReplyIsSent() throws Exception {
        long id = createTicket(login("employee@ops.local"), "VPN not working",
                "I cannot connect to the VPN from my laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");

        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());

        writeReply(agent, id, "Please restart the VPN client and try again.");

        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value(notNullValue()));
    }

    @Test
    void managementEndpointsAreForManagersOnly() throws Exception {
        mvc.perform(get("/api/analytics/summary").header("Authorization", login("employee@ops.local")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/summary").header("Authorization", login("manager@ops.local")))
                .andExpect(status().isOk());
    }

    @Test
    void weeklyReportDownloadsAsAPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/reports/weekly/pdf").header("Authorization", login("manager@ops.local")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}