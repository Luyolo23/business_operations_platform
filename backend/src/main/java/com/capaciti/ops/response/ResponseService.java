package com.capaciti.ops.response;

import com.capaciti.ops.ai.GeneratedResponse;
import com.capaciti.ops.ai.ResponseGenerator;
import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
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
            ticket.setStatus(TicketStatus.AWAITING_APPROVAL);
            ticket.setUpdatedAt(Instant.now());
            users.findByRoleIn(List.of(Role.MANAGER)).forEach(m -> notifications.notify(m, ticket.getId(),
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
            String written = body.trim();
            if (written.isEmpty()) {
                throw new BadRequestException("The reply body cannot be empty");
            }
            if (written.length() > 10000) {
                throw new BadRequestException("The reply body must be 10000 characters or fewer");
            }
            return new GeneratedResponse(written, "manual", "Written manually by " + caller.email() + ".");
        }
        ResponseTemplate template = templates.findByCategoryAndTone(ticket.getCategory(), tone)
                .orElseThrow(() -> new NotFoundException("No template for " + ticket.getCategory() + "/" + tone));
        return generator.generate(ticket, tone, template.getBody());
    }

    @Transactional(readOnly = true)
    public List<ResponseDto> list(Long ticketId, AuthUser caller) {
        ticketService.loadVisible(ticketId, caller);   // access check
        List<TicketResponse> list = responses.findByTicketIdOrderByCreatedAtDesc(ticketId);
        Map<Long, RiskAssessment> riskByResponse = compliance.latestByResponse(list);
        List<Long> ids = list.stream().map(TicketResponse::getId).toList();
        Map<Long, Long> approvalByResponse = approvals.findByResponseIdIn(ids).stream()
                .collect(Collectors.toMap(a -> a.getResponse().getId(), Approval::getId, (a, b) -> b));
        return list.stream().map(r -> toDto(r, riskByResponse.get(r.getId()), approvalByResponse.get(r.getId())))
                .toList();
    }

    /** Called when a response becomes visible to the requester (auto or after approval). */
    public void markSent(TicketResponse r, Ticket ticket) {
        r.setStatus(ResponseStatus.APPROVED);
        if (ticket.getFirstResponseAt() == null) ticket.setFirstResponseAt(Instant.now());
        if (ticket.getStatus() == TicketStatus.AWAITING_APPROVAL || ticket.getStatus() == TicketStatus.NEW
                || ticket.getStatus() == TicketStatus.ASSIGNED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticket.setUpdatedAt(Instant.now());
        notifications.notify(ticket.getRequester(), ticket.getId(), "Update on ticket #" + ticket.getId(), r.getBody());
    }

    static ResponseDto toDto(TicketResponse r, RiskAssessment risk, Long approvalId) {
        List<String> flags = risk == null || risk.getFlags() == null || risk.getFlags().isBlank()
                ? List.of() : Arrays.asList(risk.getFlags().split(","));
        return new ResponseDto(r.getId(), r.getTicket().getId(), r.getTone(), r.getBody(), r.getGeneratedBy(),
                r.getTransparencyNote(), r.getStatus(), r.getRiskLevel(), flags,
                risk == null ? null : risk.getNotes(), approvalId, r.getCreatedAt());
    }
}
