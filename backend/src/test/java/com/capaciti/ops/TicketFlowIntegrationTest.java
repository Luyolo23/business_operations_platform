package com.capaciti.ops;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capaciti.ops.compliance.AuditLog;
import com.capaciti.ops.compliance.AuditLogRepository;
import com.capaciti.ops.notification.NotificationRepository;
import com.capaciti.ops.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.SecretKey;
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
@SpringBootTest(properties = {
        "app.ai.provider=mock",
        "app.seed.enabled=true",
        "app.seed.demo-tickets=false",
        "app.jwt.secret=integration-test-secret-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
@Testcontainers          // was: disabledWithoutDocker=true — removed so Docker absence is an explicit failure, not a silent skip
@Tag("integration")      // exclude with: mvn test -DexcludedGroups=integration  (only when Docker is unavailable)
class TicketFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository auditLogs;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String JWT_SECRET = "integration-test-secret-at-least-32-bytes-long";

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
    void publicConfigIsReachableWithoutATokenAndDemoAccountsAreOffByDefault() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(false));
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

    private JsonNode createUser(String admin, String fullName, String email, String role, String department)
            throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("fullName", fullName);
        input.put("email", email);
        input.put("password", "Passw0rd!");
        input.put("role", role);
        if (department != null) input.put("department", department);
        String body = mvc.perform(post("/api/users").header("Authorization", admin).contentType(JSON)
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
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
    void invisibleCharactersCannotHideARiskyReply() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Laptop request", "I need a new laptop")
                .get("id").asLong();

        // A zero-width space used to bypass the "guarantee" rule and auto-send the reply.
        JsonNode reply = writeReply(login("agent.it@ops.local"), id,
                "We guaran\u200Btee this will be fixed tomorrow.");

        assertEquals("PENDING_APPROVAL", reply.get("status").asText());
        assertEquals("MEDIUM", reply.get("riskLevel").asText());
    }

    @Test
    void controlCharactersAreRemovedBeforeAReplyIsStored() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Laptop request", "I need a new laptop")
                .get("id").asLong();

        JsonNode reply = writeReply(login("agent.it@ops.local"), id, "Hello\u0000 there");

        assertFalse(reply.get("body").asText().contains("\u0000"));
        assertTrue(reply.get("body").asText().contains("Hello there"));
    }

    @Test
    void auditDetailsCannotBeSplitAcrossLines() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Laptop request", "I need a new laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "A laptop has been ordered for you.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content(json.writeValueAsString(Map.of(
                                "confirmed", true, "comment", "line one\nline two"))))
                .andExpect(status().isOk());

        AuditLog entry = auditLogs.findAll().stream()
                .filter(a -> "TICKET_RESOLUTION_CONFIRMED".equals(a.getAction()) && id == a.getEntityId())
                .findFirst().orElseThrow();
        assertFalse(entry.getDetails().contains("\n"), "audit details must stay on one line");
        assertFalse(entry.getDetails().contains("\r"), "audit details must stay on one line");
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
    void requesterCanConfirmThatTheProblemIsSolved() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "VPN not working", "I cannot connect to the VPN from my laptop")
                .get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "Please restart the VPN client and try again.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true,\"comment\":\"Working now, thanks\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.requesterConfirmedAt").value(notNullValue()))
                .andExpect(jsonPath("$.requesterFeedback").value("Working now, thanks"));
    }

    @Test
    void requesterCanReopenATicketThatIsStillBroken() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Printer offline", "The office printer shows an error")
                .get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "Please power the printer off and on again.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":false,\"comment\":\"Still showing an error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()))
                .andExpect(jsonPath("$.requesterFeedback").value("Still showing an error"));
    }

    @Test
    void staffStatusChangesRespectTheStateMachine() throws Exception {
        long id = createTicket(login("employee@ops.local"), "VPN not working",
                "I cannot connect to the VPN from my laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");
        String url = "/api/tickets/" + id + "/status";

        // Starting work happens through a reply; closing belongs to the requester.
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"NEW\"}"))
                .andExpect(status().isConflict());

        // Resolving still needs an answer that reached the requester.
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());

        writeReply(agent, id, "Please restart the VPN client and try again.");
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(url).header("Authorization", agent).contentType(JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheRequesterCanReopenAClosedTicket() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Printer offline", "The office printer shows an error")
                .get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "Please power the printer off and on again.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":false,\"comment\":\"It broke again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()))
                .andExpect(jsonPath("$.requesterConfirmedAt").value(nullValue()));
    }

    @Test
    void confirmingTwiceIsIdempotentAndDoesNotRepeatNotifications() throws Exception {
        String employee = login("employee@ops.local");
        long employeeId = users.findByEmailIgnoreCase("employee@ops.local").orElseThrow().getId();
        long id = createTicket(employee, "Laptop request", "I need a new laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "A laptop has been ordered for you.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        long afterFirst = confirmationsSentTo(employeeId);

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertEquals(afterFirst, confirmationsSentTo(employeeId),
                "a repeated confirmation must not notify the assignee again");
    }

    @Test
    void draftingIsBlockedOnResolvedAndClosedTickets() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "VPN not working", "I cannot connect to the VPN from my laptop")
                .get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "Please restart the VPN client and try again.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/responses").header("Authorization", agent)
                        .contentType(JSON).content("{\"tone\":\"FORMAL\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/responses").header("Authorization", agent)
                        .contentType(JSON).content("{\"tone\":\"FORMAL\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aSecondReplyIsBlockedWhileAnApprovalIsPending() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me").get("id").asLong();
        String agent = login("agent.finance@ops.local");

        JsonNode risky = writeReply(agent, id, "We guarantee you will get your money back.");
        assertEquals("PENDING_APPROVAL", risky.get("status").asText());

        mvc.perform(post("/api/tickets/" + id + "/responses").header("Authorization", agent)
                        .contentType(JSON).content("{\"tone\":\"FORMAL\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aManagerCannotApproveTheirOwnResponse() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me").get("id").asLong();
        String manager = login("manager@ops.local");

        JsonNode risky = writeReply(manager, id, "We guarantee you will get your money back.");
        assertEquals("PENDING_APPROVAL", risky.get("status").asText());
        long approvalId = risky.get("approvalId").asLong();

        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{\"approve\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApprovalDecisionNeedsAnExplicitApproveFlag() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me").get("id").asLong();
        long approvalId = writeReply(login("agent.finance@ops.local"), id,
                "We guarantee you will get your money back.").get("approvalId").asLong();
        String manager = login("manager@ops.local");

        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{\"comment\":\"missing the flag\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anApprovalCanOnlyBeDecidedOnce() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me").get("id").asLong();
        long approvalId = writeReply(login("agent.finance@ops.local"), id,
                "We guarantee you will get your money back.").get("approvalId").asLong();
        String manager = login("manager@ops.local");

        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{\"approve\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/approvals/" + approvalId + "/decision").header("Authorization", manager)
                        .contentType(JSON).content("{\"approve\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reclassificationReroutesToTheNewDepartment() throws Exception {
        String employee = login("employee@ops.local");
        JsonNode t = createTicket(employee, "VPN not working", "I cannot connect to the VPN from my laptop");
        long id = t.get("id").asLong();
        assertEquals("Ian IT-Agent", t.get("assigneeName").asText());

        mvc.perform(patch("/api/tickets/" + id + "/classification")
                        .header("Authorization", login("manager@ops.local"))
                        .contentType(JSON).content("{\"category\":\"FINANCE\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FINANCE"))
                .andExpect(jsonPath("$.assigneeName").value("Fiona Finance-Agent"));
    }

    @Test
    void aClosedTicketCannotBeReclassified() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Laptop request", "I need a new laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "A laptop has been ordered for you.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", employee)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/tickets/" + id + "/classification")
                        .header("Authorization", login("manager@ops.local"))
                        .contentType(JSON).content("{\"category\":\"FINANCE\",\"priority\":\"LOW\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void twoManagersRacingOnOneApprovalProduceOneDecision() throws Exception {
        long id = createTicket(login("employee@ops.local"), "Please guarantee my refund",
                "I was charged twice on my invoice, please refund me").get("id").asLong();
        long approvalId = writeReply(login("agent.finance@ops.local"), id,
                "We guarantee you will get your money back.").get("approvalId").asLong();
        String manager = login("manager@ops.local");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> decision = () -> {
            ready.countDown();
            go.await();
            return mvc.perform(post("/api/approvals/" + approvalId + "/decision")
                            .header("Authorization", manager).contentType(JSON)
                            .content("{\"approve\":true}"))
                    .andReturn().getResponse().getStatus();
        };
        Future<Integer> first = pool.submit(decision);
        Future<Integer> second = pool.submit(decision);
        ready.await();
        go.countDown();
        List<Integer> statuses = List.of(first.get(), second.get());
        pool.shutdownNow();

        assertEquals(1, statuses.stream().filter(s -> s == 200).count(),
                "exactly one decision should win, got " + statuses);
        assertEquals(1, statuses.stream().filter(s -> s == 409).count(),
                "the losing decision should be a conflict, got " + statuses);
    }

    private long confirmationsSentTo(long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getSubject().contains("Requester confirmed"))
                .count();
    }

    @Test
    void onlyTheRequesterCanConfirmAResolution() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Laptop request", "I need a new laptop").get("id").asLong();
        String agent = login("agent.it@ops.local");
        writeReply(agent, id, "A laptop has been ordered for you.");
        mvc.perform(patch("/api/tickets/" + id + "/status").header("Authorization", agent)
                        .contentType(JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/resolution").header("Authorization", agent)
                        .contentType(JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSwitchAnAccountOffAndOnAgain() throws Exception {
        String admin = login("admin@ops.local");
        String email = "switch-me-" + System.nanoTime() + "@ops.local";
        String body = mvc.perform(post("/api/users").header("Authorization", admin).contentType(JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Switch Person", "email", email,
                                "password", "Passw0rd!", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(patch("/api/users/" + id + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/users/" + id + "/activate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCanDeleteAnAccountThatHasNoHistory() throws Exception {
        String admin = login("admin@ops.local");
        String email = "remove-me-" + System.nanoTime() + "@ops.local";
        String body = mvc.perform(post("/api/users").header("Authorization", admin).contentType(JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Temporary Person", "email", email,
                                "password", "Passw0rd!", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(delete("/api/users/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").isEmpty());
    }

    @Test
    void nonAdminsCannotDeleteAccounts() throws Exception {
        String admin = login("admin@ops.local");
        String email = "keep-me-" + System.nanoTime() + "@ops.local";
        String body = mvc.perform(post("/api/users").header("Authorization", admin).contentType(JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "Keep Me", "email", email,
                                "password", "Passw0rd!", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(delete("/api/users/" + id).header("Authorization", login("employee@ops.local")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + id).header("Authorization", login("manager@ops.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAccountWithTicketHistoryIsKeptForAudit() throws Exception {
        String admin = login("admin@ops.local");
        String email = "has-history-" + System.nanoTime() + "@ops.local";
        String body = mvc.perform(post("/api/users").header("Authorization", admin).contentType(JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "History Person", "email", email,
                                "password", "Passw0rd!", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        String employee = login(email);
        createTicket(employee, "Cannot log in", "My password was reset but I still cannot login");

        mvc.perform(delete("/api/users/" + id).header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAnAdminUsesTheActiveAdminCount() throws Exception {
        String admin = login("admin@ops.local");
        String email = "second-admin-" + System.nanoTime() + "@ops.local";
        long id = createUser(admin, "Second Admin", email, "ADMIN", null).get("id").asLong();

        mvc.perform(patch("/api/users/" + id + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk());

        // Two ADMIN rows exist, but only one is active: the last-active-admin guard still applies.
        mvc.perform(delete("/api/users/" + id).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").isNotEmpty());
    }

    @Test
    void managementEndpointsAreForManagersOnly() throws Exception {
        mvc.perform(get("/api/analytics/summary").header("Authorization", login("employee@ops.local")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/summary").header("Authorization", login("manager@ops.local")))
                .andExpect(status().isOk());
    }

    @Test
    void forbiddenRequestsReturnTheJsonApiErrorEnvelope() throws Exception {
        mvc.perform(get("/api/analytics/summary").header("Authorization", login("employee@ops.local")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
    }

    @Test
    void employeesOnlySeeApprovedRepliesWithoutInternalRiskDetails() throws Exception {
        String employee = login("employee@ops.local");
        long id = createTicket(employee, "Refund question", "Can you tell me about my refund")
                .get("id").asLong();
        String agent = login("agent.finance@ops.local");

        JsonNode risky = writeReply(agent, id, "We guarantee you will get your money back.");
        assertEquals("PENDING_APPROVAL", risky.get("status").asText());
        long approvalId = risky.get("approvalId").asLong();

        // Staff keep the full picture, including unsent drafts.
        mvc.perform(get("/api/tickets/" + id + "/responses").header("Authorization", agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));

        // The requester must not see an unsent draft.
        mvc.perform(get("/api/tickets/" + id + "/responses").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/approvals/" + approvalId + "/decision")
                        .header("Authorization", login("manager@ops.local"))
                        .contentType(JSON).content("{\"approve\":false,\"comment\":\"not yet\"}"))
                .andExpect(status().isOk());

        // ...nor a rejected draft.
        mvc.perform(get("/api/tickets/" + id + "/responses").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        writeReply(agent, id, "Your refund is being processed.");

        // An approved reply is visible, but internal risk/review metadata stays staff-only.
        mvc.perform(get("/api/tickets/" + id + "/responses").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].riskLevel").value(nullValue()))
                .andExpect(jsonPath("$[0].riskFlags").isEmpty())
                .andExpect(jsonPath("$[0].riskNotes").value(nullValue()))
                .andExpect(jsonPath("$[0].approvalId").value(nullValue()))
                .andExpect(jsonPath("$[0].transparencyNote").value(nullValue()));
    }

    @Test
    void anAgentCanOpenATicketTheyRaisedForAnotherDepartment() throws Exception {
        String agent = login("agent.it@ops.local");
        JsonNode t = createTicket(agent, "Payslip missing for last month",
                "I did not receive my payslip and need it urgently for a loan");
        long id = t.get("id").asLong();
        assertEquals("HR", t.get("category").asText());

        mvc.perform(get("/api/tickets/" + id).header("Authorization", agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterName").value("Ian IT-Agent"));

        mvc.perform(get("/api/tickets").param("size", "100").header("Authorization", agent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").isNotEmpty());
    }

    @Test
    void inactiveAgentsAreNeverAutoAssigned() throws Exception {
        String admin = login("admin@ops.local");
        String employee = login("employee@ops.local");

        // Give the seeded IT agent at least one open ticket so the inactive agent would win on load.
        createTicket(employee, "VPN not working", "I cannot connect to the VPN from my laptop");

        String email = "inactive-it-" + System.nanoTime() + "@ops.local";
        long inactiveId = createUser(admin, "Inactive IT", email, "AGENT", "IT").get("id").asLong();
        mvc.perform(patch("/api/users/" + inactiveId + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk());

        JsonNode t = createTicket(employee, "Printer offline", "The office printer shows an error");

        assertEquals("Ian IT-Agent", t.get("assigneeName").asText());
    }

    @Test
    void manualAssignmentRejectsInactiveAndEmployeeAccounts() throws Exception {
        String admin = login("admin@ops.local");
        String manager = login("manager@ops.local");
        long ticketId = createTicket(login("employee@ops.local"), "Laptop request", "I need a new laptop")
                .get("id").asLong();

        long employeeId = createUser(admin, "Assign Target", "assign-employee-" + System.nanoTime() + "@ops.local",
                "EMPLOYEE", null).get("id").asLong();
        mvc.perform(patch("/api/tickets/" + ticketId + "/assignee").header("Authorization", manager)
                        .contentType(JSON).content(json.writeValueAsString(Map.of("userId", employeeId))))
                .andExpect(status().isForbidden());

        long inactiveAgentId = createUser(admin, "Inactive Agent",
                "assign-inactive-" + System.nanoTime() + "@ops.local", "AGENT", "IT").get("id").asLong();
        mvc.perform(patch("/api/users/" + inactiveAgentId + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/tickets/" + ticketId + "/assignee").header("Authorization", manager)
                        .contentType(JSON).content(json.writeValueAsString(Map.of("userId", inactiveAgentId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveManagersAreNotNotified() throws Exception {
        String admin = login("admin@ops.local");
        long inactiveManagerId = createUser(admin, "Inactive Manager",
                "inactive-manager-" + System.nanoTime() + "@ops.local", "MANAGER", null).get("id").asLong();
        mvc.perform(patch("/api/users/" + inactiveManagerId + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk());

        createTicket(login("employee@ops.local"), "URGENT: email down",
                "Outlook email is down for the whole team, we cannot work");

        assertTrue(notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(inactiveManagerId).isEmpty(),
                "an inactive manager must not receive new notifications");
    }

    @Test
    void tokensWithMissingClaimsAreRejectedAsUnauthorized() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        String noUserId = Jwts.builder().subject("employee@ops.local").claim("role", "EMPLOYEE")
                .signWith(key).compact();
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer " + noUserId))
                .andExpect(status().isUnauthorized());

        String noRole = Jwts.builder().subject("employee@ops.local").claim("uid", 1L)
                .signWith(key).compact();
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer " + noRole))
                .andExpect(status().isUnauthorized());
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