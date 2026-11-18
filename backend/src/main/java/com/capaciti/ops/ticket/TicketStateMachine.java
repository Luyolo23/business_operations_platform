package com.capaciti.ops.ticket;

import com.capaciti.ops.common.Exceptions.ConflictException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for ticket status changes. Every path that moves a
 * ticket (routing, replies, approvals, requester confirmation) must ask this
 * class first, so an illegal transition can never slip through one endpoint.
 */
@Component
public class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(TicketStatus.NEW, EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));
        ALLOWED.put(TicketStatus.ASSIGNED, EnumSet.of(TicketStatus.IN_PROGRESS));
        ALLOWED.put(TicketStatus.IN_PROGRESS,
                EnumSet.of(TicketStatus.AWAITING_APPROVAL, TicketStatus.RESOLVED));
        ALLOWED.put(TicketStatus.AWAITING_APPROVAL, EnumSet.of(TicketStatus.IN_PROGRESS));
        ALLOWED.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED));
        // Only the requester may move a closed ticket back to work.
        ALLOWED.put(TicketStatus.CLOSED, EnumSet.of(TicketStatus.IN_PROGRESS));
    }

    /** A no-op change (from == to) is always allowed so repeated calls stay idempotent. */
    public boolean canMove(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) return false;
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public void require(TicketStatus from, TicketStatus to) {
        if (!canMove(from, to)) {
            throw new ConflictException("A ticket cannot move from " + from + " to " + to);
        }
    }
}
