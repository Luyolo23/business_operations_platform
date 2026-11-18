package com.capaciti.ops.ticket;

import com.capaciti.ops.ai.ClassificationResult;
import com.capaciti.ops.ai.TicketClassifier;
import com.capaciti.ops.common.Exceptions.BadRequestException;
import com.capaciti.ops.common.Exceptions.ConflictException;
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
import java.util.Objects;
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
    private final TicketStateMachine stateMachine;

    public TicketService(TicketRepository tickets, UserRepository users, TicketClassifier classifier,
                         WorkflowService workflow, AuditService audit, NotificationService notifications,
                         TicketStateMachine stateMachine) {
        this.tickets = tickets;
        this.users = users;
        this.classifier = classifier;
        this.workflow = workflow;
        this.audit = audit;
        this.notifications = notifications;
        this.stateMachine = stateMachine;
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
            // An agent can always see tickets they raised, even when another department handles them.
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.equal(r.get("requester").get("id"), caller.id()),
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
        if (caller.role() == Role.EMPLOYEE) {
            throw new ForbiddenException("Only staff can change a ticket's status");
        }
        Ticket t = loadVisible(id, caller);
        TicketStatus old = t.getStatus();
        if (status == old) return TicketDto.from(t);

        // Staff may only resolve work or restart work that was marked resolved. Starting work
        // happens by replying, and closing belongs to the requester, so those targets are refused.
        if (status != TicketStatus.RESOLVED && status != TicketStatus.IN_PROGRESS) {
            throw new ConflictException("Manual status changes are limited to RESOLVED and reopening a resolved ticket");
        }
        if (status == TicketStatus.IN_PROGRESS && old != TicketStatus.RESOLVED) {
            throw new ConflictException("A ticket can only be reopened by staff once it is RESOLVED;"
                    + " start work by sending a reply");
        }
        // A ticket can only be resolved after at least one reply has reached the requester.
        // Without this the requester could be left with no answer and no way to see why it resolved.
        if (status == TicketStatus.RESOLVED && t.getFirstResponseAt() == null) {
            throw new BadRequestException(
                    "Send a reply to the requester before resolving this ticket");
        }

        transition(t, status);
        if (status == TicketStatus.RESOLVED) {
            notifications.notify(t.getRequester(), t.getId(), "Ticket #" + t.getId() + " RESOLVED",
                    "Your ticket \"" + t.getTitle() + "\" is now RESOLVED"
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
        TicketStatus old = t.getStatus();
        if (old != TicketStatus.RESOLVED && old != TicketStatus.CLOSED) {
            throw new ConflictException("This request has not been marked as resolved yet");
        }

        String feedback = comment == null ? null : comment.trim();
        if (feedback != null && feedback.isEmpty()) feedback = null;

        Instant now = Instant.now();

        // A second "yes" after the ticket is already closed changes nothing and, importantly,
        // does not send the assignee another confirmation email.
        if (confirmed && old == TicketStatus.CLOSED) {
            if (feedback != null) {
                t.setRequesterFeedback(feedback);
                t.setUpdatedAt(now);
            }
            return TicketDto.from(t);
        }

        t.setRequesterFeedback(feedback);
        t.setUpdatedAt(now);

        if (confirmed) {
            transition(t, TicketStatus.CLOSED);
            if (t.getAssignee() != null) {
                notifications.notify(t.getAssignee(), t.getId(),
                        "Requester confirmed #" + t.getId() + " is resolved",
                        t.getRequester().getFullName() + " confirmed that \"" + t.getTitle()
                                + "\" is solved. The ticket is now closed.");
            }
            audit.log("TICKET_RESOLUTION_CONFIRMED", "Ticket", t.getId(), caller.email(),
                    feedback == null ? null : "feedback=" + feedback);
        } else {
            // Either RESOLVED -> IN_PROGRESS or CLOSED -> IN_PROGRESS; both clear the resolution times.
            transition(t, TicketStatus.IN_PROGRESS);
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

    /**
     * Human override of the AI classification (feeds the "manual override rate" compliance metric).
     * When the department changes, the ticket is routed to the new department: the current
     * assignee stays only if they are an active agent there, otherwise the least-loaded agent takes it.
     */
    @Transactional
    public TicketDto reclassify(Long id, Category category, Priority priority, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw new ConflictException("A closed ticket cannot be reclassified");
        }
        String before = t.getCategory() + "/" + t.getPriority();
        Category oldCategory = t.getCategory();
        User previous = t.getAssignee();
        t.setCategory(category);
        t.setPriority(priority);
        t.setClassificationSource("MANUAL");
        t.setClassificationConfidence(1.0);

        String routingNote = "assignee unchanged";
        if (oldCategory != category) {
            Category department = workflow.departmentFor(category);
            boolean stillValid = previous != null && previous.isActive()
                    && previous.getRole() == Role.AGENT
                    && previous.getDepartment() == department;
            if (!stillValid) {
                User replacement = workflow.pickAgent(department).orElse(null);
                t.setAssignee(replacement);
                if (replacement != null && t.getStatus() == TicketStatus.NEW) {
                    transition(t, TicketStatus.ASSIGNED);
                }
                if (previous != null && !Objects.equals(previous.getId(),
                        replacement == null ? null : replacement.getId())) {
                    notifications.notify(previous, t.getId(), "Ticket #" + t.getId() + " reclassified",
                            "\"" + t.getTitle() + "\" moved to " + category
                                    + " and is no longer assigned to you.");
                }
                if (replacement != null) {
                    notifications.notify(replacement, t.getId(), "Ticket assigned: #" + t.getId(),
                            "\"" + t.getTitle() + "\" was reclassified to " + category
                                    + " and assigned to you by " + caller.email() + ".");
                }
                routingNote = "assignee=" + (replacement == null ? "none" : replacement.getEmail());
            }
        }
        t.setUpdatedAt(Instant.now());
        audit.log("TICKET_RECLASSIFIED", "Ticket", t.getId(), caller.email(),
                before + " -> " + category + "/" + priority + " (" + routingNote + ")");
        return TicketDto.from(t);
    }

    @Transactional
    public TicketDto assign(Long id, Long userId, AuthUser caller) {
        Ticket t = loadVisible(id, caller);
        User assignee = loadUser(userId);
        if (assignee.getRole() == Role.EMPLOYEE) throw new ForbiddenException("Cannot assign to an employee");
        if (!assignee.isActive()) throw new ForbiddenException("Cannot assign to an inactive user");
        t.setAssignee(assignee);
        if (t.getStatus() == TicketStatus.NEW) transition(t, TicketStatus.ASSIGNED);
        t.setUpdatedAt(Instant.now());
        notifications.notify(assignee, t.getId(), "Ticket assigned: #" + t.getId(),
                "\"" + t.getTitle() + "\" was assigned to you by " + caller.email() + ".");
        audit.log("TICKET_ASSIGNED", "Ticket", t.getId(), caller.email(), "assignee=" + assignee.getEmail());
        return TicketDto.from(t);
    }

    /**
     * Applies a validated status change and keeps the resolution timestamps consistent.
     * All status writes should go through here so no endpoint can bypass the state machine.
     */
    @Transactional
    public void transition(Ticket t, TicketStatus target) {
        TicketStatus from = t.getStatus();
        if (from == target) return;
        stateMachine.require(from, target);

        Instant now = Instant.now();
        t.setStatus(target);
        t.setUpdatedAt(now);

        switch (target) {
            case RESOLVED -> {
                if (t.getResolvedAt() == null) t.setResolvedAt(now);
                t.setRequesterConfirmedAt(null);
            }
            case CLOSED -> {
                if (t.getResolvedAt() == null) t.setResolvedAt(now);
                t.setRequesterConfirmedAt(now);
            }
            case IN_PROGRESS -> {
                if (from == TicketStatus.RESOLVED || from == TicketStatus.CLOSED) {
                    t.setResolvedAt(null);
                    t.setRequesterConfirmedAt(null);
                }
            }
            default -> { }
        }
    }

    /** Shared access rule used by the response module too. */
    @Transactional(readOnly = true)
    public Ticket loadVisible(Long id, AuthUser caller) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new NotFoundException("Ticket " + id + " not found"));
        boolean allowed = switch (caller.role()) {
            case ADMIN, MANAGER -> true;
            case EMPLOYEE -> t.getRequester().getId().equals(caller.id());
            // Staff can see tickets they raised themselves, even if another department owns them.
            case AGENT -> t.getRequester().getId().equals(caller.id())
                    || (t.getAssignee() != null && t.getAssignee().getId().equals(caller.id()))
                    || t.getCategory() == loadUser(caller.id()).getDepartment();
        };
        if (!allowed) throw new ForbiddenException("You do not have access to this ticket");
        return t;
    }

    private User loadUser(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
