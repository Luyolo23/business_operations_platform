package com.capaciti.ops.response;

import com.capaciti.ops.ai.GeneratedResponse;
import com.capaciti.ops.ai.ResponseGenerator;
import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.ConflictException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.common.TextSafety;
import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.compliance.ComplianceService;
import com.capaciti.ops.compliance.RiskAssessment;
import com.capaciti.ops.compliance.RiskLevel;
import com.capaciti.ops.notification.NotificationService;
import com.capaciti.ops.response.ResponseDtos.ResponseDto;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketService;
import com.capaciti.ops.ticket.TicketStatus;
import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Week 2 (generate) + Week 6 (risk check) + Week 7 (approval workflow).
 * LOW / NONE risk -> auto-approved and sent. MEDIUM / HIGH risk -> held for manager approval.
 */
@Service
public class ResponseService {

    private final TicketService ticketService;
    private final ResponseTemplateRepository templates;
    private final TicketResponseRepository responses;
    private final ApprovalRepository approvals;
    private final ResponseGenerator generator;
    private final ComplianceService compliance;
    private final NotificationService notifications;
    private final AuditService audit;
    private final UserRepository users;

    public ResponseService(TicketService ticketService, ResponseTemplateRepository templates,
                           TicketResponseRepository responses, ApprovalRepository approvals,
                           ResponseGenerator generator, ComplianceService compliance,
                           NotificationService notifications, AuditService audit, UserRepository users) {
        this.ticketService = ticketService;
        this.templates = templates;
        this.responses = responses;
        this.approvals = approvals;
        this.generator = generator;
        this.compliance = compliance;
        this.notifications = notifications;
        this.audit = audit;
        this.users = users;
    }

    @Transactional
    public ResponseDto generate(Long ticketId, Tone tone, String body, AuthUser caller) {
        Ticket ticket = ticketService.loadVisible(ticketId, caller);
        ensureCanReply(ticket);
        GeneratedResponse g = draft(ticket, tone, body, caller);

        TicketResponse r = new TicketResponse();
        r.setTicket(ticket);
        r.setTone(tone);
        r.setBody(g.body());
        r.setGeneratedBy(g.generatedBy());
        r.setTransparencyNote(g.transparencyNote() + " Classification: " + ticket.getClassificationSource()
                + " (confidence " + ticket.getClassificationConfidence() + ").");
        r.setCreatedBy(users.findById(caller.id()).orElse(null));
        r.setStatus(ResponseStatus.PENDING_APPROVAL);
        r.setRiskLevel(RiskLevel.NONE);
        responses.save(r);

        RiskAssessment risk = compliance.assess(r);
        r.setRiskLevel(risk.getLevel());

        Long approvalId = null;
        if (risk.getLevel() == RiskLevel.MEDIUM || risk.getLevel() == RiskLevel.HIGH) {
            Approval a = new Approval();
            a.setResponse(r);
            a.setStatus(ApprovalStatus.PENDING);
            approvals.save(a);
            approvalId = a.getId();
            // The reply is held, but work has started: NEW/ASSIGNED moves to IN_PROGRESS first,
            // then IN_PROGRESS to AWAITING_APPROVAL. Both steps are validated by the state machine.
            ticketService.transition(ticket, TicketStatus.IN_PROGRESS);
            ticketService.transition(ticket, TicketStatus.AWAITING_APPROVAL);
            users.findByRoleInAndActiveTrue(List.of(Role.MANAGER)).forEach(m -> notifications.notify(m, ticket.getId(),
                    "Approval needed: ticket #" + ticket.getId(),
                    "A " + risk.getLevel() + " risk response needs your review (" + risk.getFlags() + ")."));
            audit.log("RESPONSE_HELD_FOR_APPROVAL", "TicketResponse", r.getId(), caller.email(),
                    "risk=" + risk.getLevel() + " flags=" + risk.getFlags());
        } else {
            markSent(r, ticket);
            audit.log("RESPONSE_AUTO_APPROVED", "TicketResponse", r.getId(), caller.email(), "risk=" + risk.getLevel());
        }
        return toDto(r, risk, approvalId);
    }

    /**
     * Manual replies come in as a plain {@code body}; everything else is drafted by the
     * configured generator. Both paths are risk-screened identically in {@link #generate}.
     */
    private GeneratedResponse draft(Ticket ticket, Tone tone, String body, AuthUser caller) {
        if (body != null) {
            String written = TextSafety.cleanReply(body);
            requireValidReply(written);
            return new GeneratedResponse(written, "manual", "Written manually by " + caller.email() + ".");
        }
        ResponseTemplate template = templates.findByCategoryAndTone(ticket.getCategory(), tone)
                .orElseThrow(() -> new NotFoundException("No template for " + ticket.getCategory() + "/" + tone));
        GeneratedResponse generated = generator.generate(ticket, tone, template.getBody());
        String cleaned = TextSafety.cleanReply(generated.body());
        requireValidReply(cleaned);
        return new GeneratedResponse(cleaned, generated.generatedBy(), generated.transparencyNote());
    }

    private static void requireValidReply(String text) {
        if (text.isEmpty()) {
            throw new BadRequestException("The reply body cannot be empty");
        }
        if (text.length() > 10000) {
            throw new BadRequestException("The reply body must be 10000 characters or fewer");
        }
    }

    @Transactional(readOnly = true)
    public List<ResponseDto> list(Long ticketId, AuthUser caller) {
        ticketService.loadVisible(ticketId, caller);   // access check
        List<TicketResponse> list = responses.findByTicketIdOrderByCreatedAtDesc(ticketId);

        // Requesters are not reviewers: hide unsent/rejected drafts and every internal
        // risk/approval field. Return before loading risk data so employees cost no extra queries.
        if (caller.role() == Role.EMPLOYEE) {
            return list.stream()
                    .filter(r -> r.getStatus() == ResponseStatus.APPROVED)
                    .map(ResponseService::toRequesterDto)
                    .toList();
        }

        Map<Long, RiskAssessment> riskByResponse = compliance.latestByResponse(list);
        List<Long> ids = list.stream().map(TicketResponse::getId).toList();
        Map<Long, Long> approvalByResponse = approvals.findByResponseIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.getResponse().getId(), Approval::getId, (a, b) -> b));
        return list.stream().map(r -> toDto(r, riskByResponse.get(r.getId()), approvalByResponse.get(r.getId())))
                .toList();
    }

    /**
     * Drafting is only meaningful while work is open and no other reply is waiting for a
     * decision. Resolved/closed tickets must be reopened, and one pending approval blocks
     * a second draft so two managers cannot approve competing replies.
     */
    private void ensureCanReply(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("This ticket is " + ticket.getStatus()
                    + "; reopen it before sending another reply");
        }
        if (ticket.getStatus() == TicketStatus.AWAITING_APPROVAL
                || approvals.existsByStatusAndResponseTicketId(ApprovalStatus.PENDING, ticket.getId())) {
            throw new ConflictException("A reply on this ticket is already awaiting manager approval");
        }
    }

    /** Called when a response becomes visible to the requester (auto or after approval). */
    public void markSent(TicketResponse r, Ticket ticket) {
        r.setStatus(ResponseStatus.APPROVED);
        if (ticket.getFirstResponseAt() == null) ticket.setFirstResponseAt(Instant.now());
        // Valid from NEW/ASSIGNED (first reply), IN_PROGRESS (another update) and
        // AWAITING_APPROVAL (a manager just approved the held reply).
        ticketService.transition(ticket, TicketStatus.IN_PROGRESS);
        notifications.notify(ticket.getRequester(), ticket.getId(), "Update on ticket #" + ticket.getId(), r.getBody());
    }

    static ResponseDto toDto(TicketResponse r, RiskAssessment risk, Long approvalId) {
        List<String> flags = risk == null || risk.getFlags() == null || risk.getFlags().isBlank()
                ? List.of() : Arrays.asList(risk.getFlags().split(","));
        return new ResponseDto(r.getId(), r.getTicket().getId(), r.getTone(), r.getBody(), r.getGeneratedBy(),
                r.getTransparencyNote(), r.getStatus(), r.getRiskLevel(), flags,
                risk == null ? null : risk.getNotes(), approvalId, r.getCreatedAt());
    }

    /** The requester's view: the delivered reply only, with no internal review metadata. */
    private static ResponseDto toRequesterDto(TicketResponse r) {
        return new ResponseDto(r.getId(), r.getTicket().getId(), r.getTone(), r.getBody(), r.getGeneratedBy(),
                null, ResponseStatus.APPROVED, null, List.of(), null, null, r.getCreatedAt());
    }
}
