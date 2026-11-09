package com.capaciti.ops.ticket;

import com.capaciti.ops.ai.ClassificationResult;
import com.capaciti.ops.ai.TicketClassifier;
import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.ForbiddenException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.common.PageResponse;
import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.notification.NotificationService;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.ticket.TicketDtos.*;
import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import com.capaciti.ops.workflow.WorkflowService;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final UserRepository users;
    private final TicketClassifier classifier;
    private final WorkflowService workflow;
    private final AuditService audit;
    private final NotificationService notifications;

    public TicketService(TicketRepository tickets, UserRepository users, TicketClassifier classifier,
                         WorkflowService workflow, AuditService audit, NotificationService notifications) {
        this.tickets = tickets;
        this.users = users;
        this.classifier = classifier;
        this.workflow = workflow;
        this.audit = audit;
        this.notifications = notifications;
    }

    /** Week 1: create -> classify -> store -> (Week 7) route & notify. */
    @Transactional
    public TicketDto create(CreateTicketRequest req, AuthUser caller) {
        User requester = loadUser(caller.id());
        ClassificationResult c = classifier.classify(req.title(), req.description());

        Ticket t = new Ticket();
        t.setTitle(req.title().trim());
        t.setDescription(req.description().trim());
        t.setRequester(requester);
        t.setCategory(c.category());
        t.setPriority(c.priority());
        t.setStatus(TicketStatus.NEW);
        t.setClassificationConfidence(c.confidence());
        t.setClassificationSource(c.source());
        tickets.save(t);

        audit.log("TICKET_CREATED", "Ticket", t.getId(), caller.email(), null);
        audit.log("TICKET_CLASSIFIED", "Ticket", t.getId(), c.source(),
                "category=" + c.category() + " priority=" + c.priority() + " confidence=" + c.confidence()
                        + " rationale=" + c.rationale());
        workflow.routeAndNotify(t);
        return TicketDto.from(t);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketDto> list(Category category, TicketStatus status, Priority priority,
                                        int page, int size, AuthUser caller) {
        Specification<Ticket> spec = Specification.where(null);
        if (category != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("category"), category));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (priority != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("priority"), priority));

        if (caller.role() == Role.EMPLOYEE) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("requester").get("id"), caller.id()));
        } else if (caller.role() == Role.AGENT) {
            Category dept = loadUser(caller.id()).getDepartment();
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.equal(r.get("assignee").get("id"), caller.id()),
                    dept == null ? cb.disjunction() : cb.equal(r.get("category"), dept)));
        }
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> result = tickets.findAll(spec, pr);
        return PageResponse.from(result, TicketDto::from);
    }

    @Transactional(readOnly = true)
    public TicketDto get(Long id, AuthUser caller) {
        return TicketDto.from(loadVisible(id, caller));
    }

    @Transactional
    public TicketDto updateStatus(Long id, TicketStatus status, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        TicketStatus old = t.getStatus();
        // A ticket can only be resolved/closed after at least one reply has reached the requester.
        // Without this the requester could be left with no answer and no way to see why it closed.
        if ((status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) && t.getFirstResponseAt() == null) {
            throw new BadRequestException(
                    "Send a reply to the requester before resolving or closing this ticket");
        }
        t.setStatus(status);
        t.setUpdatedAt(Instant.now());
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            if (t.getResolvedAt() == null) t.setResolvedAt(Instant.now());
            notifications.notify(t.getRequester(), t.getId(), "Ticket #" + t.getId() + " " + status,
                    "Your ticket \"" + t.getTitle() + "\" is now " + status
                            + ". Open it to confirm whether the problem is solved.");
        }
        audit.log("TICKET_STATUS_CHANGED", "Ticket", t.getId(), caller.email(), old + " -> " + status);
        return TicketDto.from(t);
    }

    /**
     * The person who raised the ticket has the final say: confirm the fix (closes the ticket)
     * or say the problem is still there (reopens it for the team).
     */
    @Transactional
    public TicketDto confirmResolution(Long id, boolean confirmed, String comment, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        if (!t.getRequester().getId().equals(caller.id())) {
            throw new ForbiddenException("Only the person who raised this request can confirm the resolution");
        }
        if (t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED) {
            throw new BadRequestException("This request has not been marked as resolved yet");
        }

        String feedback = comment == null ? null : comment.trim();
        if (feedback != null && feedback.isEmpty()) feedback = null;

        Instant now = Instant.now();
        t.setRequesterFeedback(feedback);
        t.setUpdatedAt(now);

        if (confirmed) {
            t.setStatus(TicketStatus.CLOSED);
            t.setRequesterConfirmedAt(now);
            if (t.getResolvedAt() == null) t.setResolvedAt(now);
            if (t.getAssignee() != null) {
                notifications.notify(t.getAssignee(), t.getId(),
                        "Requester confirmed #" + t.getId() + " is resolved",
                        t.getRequester().getFullName() + " confirmed that \"" + t.getTitle()
                                + "\" is solved. The ticket is now closed.");
            }
            audit.log("TICKET_RESOLUTION_CONFIRMED", "Ticket", t.getId(), caller.email(),
                    feedback == null ? null : "feedback=" + feedback);
        } else {
            t.setStatus(TicketStatus.IN_PROGRESS);
            t.setResolvedAt(null);
            t.setRequesterConfirmedAt(null);
            String message = t.getRequester().getFullName() + " says \"" + t.getTitle()
                    + "\" is still not resolved." + (feedback == null ? "" : " They added: " + feedback);
            if (t.getAssignee() != null) {
                notifications.notify(t.getAssignee(), t.getId(), "Requester reopened #" + t.getId(), message);
            } else {
                users.findByRoleInAndActiveTrue(List.of(Role.MANAGER, Role.ADMIN))
                        .forEach(manager -> notifications.notify(manager, t.getId(),
                                "Requester reopened #" + t.getId(), message));
            }
            audit.log("TICKET_REOPENED_BY_REQUESTER", "Ticket", t.getId(), caller.email(),
                    feedback == null ? null : "feedback=" + feedback);
        }
        return TicketDto.from(t);
    }

    /** Human override of the AI classification (feeds the "manual override rate" compliance metric). */
    @Transactional
    public TicketDto reclassify(Long id, Category category, Priority priority, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        String before = t.getCategory() + "/" + t.getPriority();
        t.setCategory(category);
        t.setPriority(priority);
        t.setClassificationSource("MANUAL");
        t.setClassificationConfidence(1.0);
        t.setUpdatedAt(Instant.now());
        audit.log("TICKET_RECLASSIFIED", "Ticket", t.getId(), caller.email(),
                before + " -> " + category + "/" + priority);
        return TicketDto.from(t);
    }

    @Transactional
    public TicketDto assign(Long id, Long userId, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        User assignee = loadUser(userId);
        if (assignee.getRole() == Role.EMPLOYEE) throw new ForbiddenException("Cannot assign to an employee");
        t.setAssignee(assignee);
        if (t.getStatus() == TicketStatus.NEW) t.setStatus(TicketStatus.ASSIGNED);
        t.setUpdatedAt(Instant.now());
        notifications.notify(assignee, t.getId(), "Ticket assigned: #" + t.getId(),
                "\"" + t.getTitle() + "\" was assigned to you by " + caller.email() + ".");
        audit.log("TICKET_ASSIGNED", "Ticket", t.getId(), caller.email(), "assignee=" + assignee.getEmail());
        return TicketDto.from(t);
    }

    /** Shared access rule used by the response module too. */
    @Transactional(readOnly = true)
    public Ticket loadVisible(Long id, AuthUser caller) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
        boolean allowed = switch (caller.role()) {
            case ADMIN, MANAGER -> true;
            case EMPLOYEE -> t.getRequester().getId().equals(caller.id());
            case AGENT -> (t.getAssignee() != null && t.getAssignee().getId().equals(caller.id()))
                    || t.getCategory() == loadUser(caller.id()).getDepartment();
        };
        if (!allowed) throw new ForbiddenException("You do not have access to this ticket");
        return t;
    }

    private User loadUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
