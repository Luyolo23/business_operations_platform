package com.capaciti.ops.ticket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capaciti.ops.common.Exceptions.ConflictException;
import org.junit.jupiter.api.Test;

class TicketStateMachineTest {

    private final TicketStateMachine machine = new TicketStateMachine();

    @Test
    void allowsEveryApprovedTransition() {
        assertTrue(machine.canMove(TicketStatus.NEW, TicketStatus.ASSIGNED));
        assertTrue(machine.canMove(TicketStatus.NEW, TicketStatus.IN_PROGRESS));
        assertTrue(machine.canMove(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));
        assertTrue(machine.canMove(TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_APPROVAL));
        assertTrue(machine.canMove(TicketStatus.AWAITING_APPROVAL, TicketStatus.IN_PROGRESS));
        assertTrue(machine.canMove(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        assertTrue(machine.canMove(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS));
        assertTrue(machine.canMove(TicketStatus.RESOLVED, TicketStatus.CLOSED));
        assertTrue(machine.canMove(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS));
    }

    @Test
    void treatsARepeatedStatusAsANoOp() {
        for (TicketStatus status : TicketStatus.values()) {
            assertTrue(machine.canMove(status, status), status + " -> " + status + " should be a no-op");
            assertDoesNotThrow(() -> machine.require(status, status));
        }
    }

    @Test
    void rejectsSkippingWorkStates() {
        assertFalse(machine.canMove(TicketStatus.NEW, TicketStatus.RESOLVED));
        assertFalse(machine.canMove(TicketStatus.NEW, TicketStatus.CLOSED));
        assertFalse(machine.canMove(TicketStatus.NEW, TicketStatus.AWAITING_APPROVAL));
        assertFalse(machine.canMove(TicketStatus.ASSIGNED, TicketStatus.RESOLVED));
        assertFalse(machine.canMove(TicketStatus.ASSIGNED, TicketStatus.CLOSED));
        assertFalse(machine.canMove(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED));
        assertFalse(machine.canMove(TicketStatus.AWAITING_APPROVAL, TicketStatus.RESOLVED));
        assertFalse(machine.canMove(TicketStatus.AWAITING_APPROVAL, TicketStatus.CLOSED));
    }

    @Test
    void rejectsMovingBackwardsIntoAnUnstartedState() {
        assertFalse(machine.canMove(TicketStatus.ASSIGNED, TicketStatus.NEW));
        assertFalse(machine.canMove(TicketStatus.IN_PROGRESS, TicketStatus.NEW));
        assertFalse(machine.canMove(TicketStatus.IN_PROGRESS, TicketStatus.ASSIGNED));
        assertFalse(machine.canMove(TicketStatus.RESOLVED, TicketStatus.ASSIGNED));
        assertFalse(machine.canMove(TicketStatus.CLOSED, TicketStatus.RESOLVED));
        assertFalse(machine.canMove(TicketStatus.CLOSED, TicketStatus.NEW));
    }

    @Test
    void requireThrowsAConflictForAnIllegalMove() {
        ConflictException ex = assertThrows(ConflictException.class,
                () -> machine.require(TicketStatus.NEW, TicketStatus.CLOSED));
        assertTrue(ex.getMessage().contains("NEW") && ex.getMessage().contains("CLOSED"),
                "the message should name both states, was: " + ex.getMessage());
    }
}
