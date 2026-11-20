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

    @Test
    void classifiesLoanRequestAsFinance() {
        var r = classifier.classify("Loan application",
                "I would like to apply for a staff loan to cover my child's school fees.");
        assertEquals(Category.FINANCE, r.category());
        assertTrue(r.confidence() >= 0.4, "a matched category must beat the 0.30 no-match floor");
    }

    @Test
    void classifiesSalaryAdvanceAsFinance() {
        var r = classifier.classify("Salary advance request",
                "Please deduct the advance from my pay over three months.");
        assertEquals(Category.FINANCE, r.category());
        assertTrue(r.confidence() >= 0.4);
    }

    @Test
    void pluralLoanKeywordStillMatches() {
        var r = classifier.classify("Staff loans", "Which loans can I apply for?");
        assertEquals(Category.FINANCE, r.category());
    }

    @Test
    void nullInputDoesNotThrow() {
        var r = classifier.classify(null, null);
        assertEquals(Category.OPERATIONS, r.category());
        assertEquals(Priority.MEDIUM, r.priority());
        assertTrue(r.confidence() <= 0.3);
    }

    @Test
    void routesOneRepresentativeTicketPerCategory() {
        assertEquals(Category.IT, classifier.classify("VPN access", "I cannot connect from my laptop").category());
        assertEquals(Category.HR, classifier.classify("Annual leave", "I want to book leave next month").category());
        assertEquals(Category.FINANCE, classifier.classify("Invoice query", "Our supplier invoice is unpaid").category());
        assertEquals(Category.OPERATIONS, classifier.classify("Stock delivery", "The warehouse delivery is late").category());
    }
}
