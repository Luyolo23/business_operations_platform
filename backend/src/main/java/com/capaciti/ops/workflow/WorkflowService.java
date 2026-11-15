package com.capaciti.ops.workflow;

import com.capaciti.ops.compliance.AuditService;
import com.capaciti.ops.notification.NotificationService;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketRepository;
import com.capaciti.ops.ticket.TicketStateMachine;
import com.capaciti.ops.ticket.TicketStatus;
import com.capaciti.ops.user.Role;
import com.capaciti.ops.user.User;
import com.capaciti.ops.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Week 7: end-to-end automation - routing, notifications and SLA escalation. */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final Set<TicketStatus> OPEN_STATUSES = EnumSet.of(TicketStatus.NEW, TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_APPROVAL);

    private final RoutingRuleRepository rules;
    private final UserRepository users;
    private final TicketRepository tickets;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TicketStateMachine stateMachine;

    public WorkflowService(RoutingRuleRepository rules, UserRepository users, TicketRepository tickets,
                           NotificationService notifications, AuditService audit,
                           TicketStateMachine stateMachine) {
        this.rules = rules;
        this.users = users;
        this.tickets = tickets;
        this.notifications = notifications;
        this.audit = audit;
        this.stateMachine = stateMachine;
    }

    /** Which department a category is routed to (a routing rule may override the category itself). */
    public Category departmentFor(Category category) {
        return rules.findByCategoryAndActiveTrue(category)
                .map(RoutingRule::getTargetDepartment).orElse(category);
    }

    /** The active agent in a department with the fewest open tickets, if any. */
    public Optional<User> pickAgent(Category department) {
        return users.findByRoleAndDepartmentAndActiveTrue(Role.AGENT, department).stream()
                .min(Comparator.comparingLong(a -> tickets.countByAssigneeIdAndStatusIn(a.getId(), OPEN_STATUSES)));
    }

    /** Called right after a ticket is created and classified. */
    @Transactional
    public void routeAndNotify(Ticket ticket) {
        Category department = departmentFor(ticket.getCategory());
        Optional<User> agent = pickAgent(department);

        agent.ifPresent(a -> {
            stateMachine.require(ticket.getStatus(), TicketStatus.ASSIGNED);
            ticket.setAssignee(a);
            ticket.setStatus(TicketStatus.ASSIGNED);
        });
        ticket.setUpdatedAt(Instant.now());
        audit.log("TICKET_ROUTED", "Ticket", ticket.getId(), "system",
                "category=" + ticket.getCategory() + " department=" + department
                        + " assignee=" + agent.map(User::getEmail).orElse("none"));

        notifications.notify(ticket.getRequester(), ticket.getId(), "Ticket #" + ticket.getId() + " received",
                "We received \"" + ticket.getTitle() + "\" and routed it to " + department + ".");
        agent.ifPresent(a -> notifications.notify(a, ticket.getId(), "New ticket assigned: #" + ticket.getId(),
                "\"" + ticket.getTitle() + "\" (" + ticket.getPriority() + ") was assigned to you."));

        if (ticket.getPriority() == Priority.URGENT) notifyManagers(ticket, "Urgent ticket #" + ticket.getId(),
                "\"" + ticket.getTitle() + "\" was raised as URGENT.");
    }

    /** Runs every 15 minutes: escalate open tickets that have exceeded the SLA for their category. */
    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void escalateOverdue() {
        List<Ticket> open = tickets.findByStatusInAndEscalatedFalse(OPEN_STATUSES);
        Instant now = Instant.now();
        int count = 0;
        for (Ticket t : open) {
            int slaHours = rules.findByCategoryAndActiveTrue(t.getCategory()).map(RoutingRule::getSlaHours).orElse(48);
            if (Duration.between(t.getCreatedAt(), now).toHours() >= slaHours) {
                t.setEscalated(true);
                notifyManagers(t, "SLA breach: ticket #" + t.getId(),
                        "\"" + t.getTitle() + "\" is open longer than the " + slaHours + "h SLA.");
                audit.log("TICKET_ESCALATED", "Ticket", t.getId(), "system", "sla=" + slaHours + "h");
                count++;
            }
        }
        if (count > 0) log.info("Escalated {} overdue ticket(s)", count);
    }

    private void notifyManagers(Ticket t, String subject, String message) {
        users.findByRoleInAndActiveTrue(List.of(Role.MANAGER))
                .forEach(m -> notifications.notify(m, t.getId(), subject, message));
    }
}
