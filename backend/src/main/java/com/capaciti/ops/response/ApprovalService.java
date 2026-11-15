package com.capaciti.ops.response;

import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.ConflictException;
import com.capaciti.ops.common.Exceptions.ForbiddenException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.compliance.ComplianceService;
import com.capaciti.ops.compliance.RiskAssessment;
import com.capaciti.ops.notification.NotificationService;
import com.capaciti.ops.response.ResponseDtos.ApprovalDto;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketService;
import com.capaciti.ops.ticket.TicketStatus;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

    private final ApprovalRepository approvals;
    private final ResponseService responseService;
    private final TicketService ticketService;
    private final ComplianceService compliance;
    private final NotificationService notifications;
    private final AuditService audit;
    private final UserRepository users;

    public ApprovalService(ApprovalRepository approvals, ResponseService responseService,
                           TicketService ticketService, ComplianceService compliance,
                           NotificationService notifications, AuditService audit, UserRepository users) {
        this.approvals = approvals;
        this.responseService = responseService;
        this.ticketService = ticketService;
        this.compliance = compliance;
        this.notifications = notifications;
        this.audit = audit;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<ApprovalDto> list(ApprovalStatus status) {
        ApprovalStatus s = status == null ? ApprovalStatus.PENDING : status;
        return approvals.findByStatusOrderByCreatedAtAsc(s).stream().map(this::toDto).toList();
    }

    @Transactional
    public ApprovalDto decide(Long approvalId, Boolean approve, String comment, AuthUser caller) {
        if (approve == null) throw new BadRequestException("The approve field is required");
        Approval a = approvals.findById(approvalId).orElseThrow(() -> new NotFoundException("Approval not found"));

        // Two managers may race to decide the same approval. The second one must not
        // overwrite the first decision, so a settled approval is a conflict, not a bad request.
        if (a.getStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("This approval has already been decided");
        }
        User decider = users.findById(caller.id()).orElseThrow(() -> new NotFoundException("User not found"));
        TicketResponse r = a.getResponse();
        Ticket ticket = r.getTicket();

        if (ticket.getStatus() != TicketStatus.AWAITING_APPROVAL) {
            throw new ConflictException("Ticket #" + ticket.getId() + " is not awaiting approval");
        }
        // Independent review: whoever wrote the reply cannot also approve it.
        if (r.getCreatedBy() != null && r.getCreatedBy().getId().equals(caller.id())) {
            throw new ForbiddenException("You cannot decide on a response you wrote yourself");
        }

        a.setDecidedBy(decider);
        a.setDecidedAt(Instant.now());
        a.setDecisionComment(comment);
        if (approve) {
            a.setStatus(ApprovalStatus.APPROVED);
            responseService.markSent(r, ticket);
        } else {
            a.setStatus(ApprovalStatus.REJECTED);
            r.setStatus(ResponseStatus.REJECTED);
            // Only AWAITING_APPROVAL -> IN_PROGRESS is legal here; the state machine enforces it.
            ticketService.transition(ticket, TicketStatus.IN_PROGRESS);
            notifications.notify(r.getCreatedBy(), ticket.getId(), "Response rejected: ticket #" + ticket.getId(),
                    "A manager rejected the drafted response. " + (comment == null ? "" : comment));
        }
        audit.log(approve ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED", "Approval", a.getId(), caller.email(), comment);
        return toDto(a);
    }

    private ApprovalDto toDto(Approval a) {
        TicketResponse r = a.getResponse();
        RiskAssessment risk = compliance.latestByResponse(List.of(r)).get(r.getId());
        List<String> flags = risk == null || risk.getFlags() == null || risk.getFlags().isBlank()
                ? List.of() : Arrays.asList(risk.getFlags().split(","));
        return new ApprovalDto(a.getId(), r.getId(), r.getTicket().getId(), r.getTicket().getTitle(), r.getBody(),
                r.getRiskLevel(), flags, a.getStatus(),
                a.getDecidedBy() == null ? null : a.getDecidedBy().getFullName(),
                a.getDecisionComment(), a.getCreatedAt(), a.getDecidedAt());
    }
}
