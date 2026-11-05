package com.capaciti.ops.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import org.junit.jupiter.api.Test;

class KeywordTicketClassifierTest {

    private final KeywordTicketClassifier classifier = new KeywordTicketClassifier();

    @Test
    void classifiesItTicket() {
        var r = classifier.classify("VPN not working", "I cannot connect to the VPN from my laptop");
        assertEquals(Category.IT, r.category());
        assertEquals(Priority.HIGH, r.priority());
    }

    @Test
    void classifiesFinanceTicket() {
        var r = classifier.classify("Expense reimbursement", "My expense claim has not been paid");
        assertEquals(Category.FINANCE, r.category());
    }

    @Test
    void detectsUrgentPriority() {
        var r = classifier.classify("URGENT: email down", "Whole team cannot work");
        assertEquals(Priority.URGENT, r.priority());
    }

    @Test
    void unknownTextDefaultsToOperationsWithLowConfidence() {
        var r = classifier.classify("zzz", "qqq");
        assertEquals(Category.OPERATIONS, r.category());
        assertTrue(r.confidence() <= 0.3);
    }
}
