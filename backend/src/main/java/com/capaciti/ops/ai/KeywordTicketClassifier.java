package com.capaciti.ops.ai;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic, offline classifier. Also used as the fallback when the LLM is unavailable. */
@Component
public class KeywordTicketClassifier implements TicketClassifier {

    private static final Map<Category, List<String>> KEYWORDS = new EnumMap<>(Category.class);
    private static final List<String> URGENT = List.of("urgent", "asap", "immediately", "emergency", "outage",
            "down", "cannot work", "critical", "right now");
    private static final List<String> HIGH = List.of("not working", "broken", "error", "crash", "blocked",
            "unpaid", "locked", "failed", "cannot", "can't", "unable", "deadline");
    private static final List<String> LOW = List.of("question", "how do i", "how to", "information", "enquiry",
            "inquiry", "when is", "what is", "suggestion");

    static {
        KEYWORDS.put(Category.IT, List.of("password", "login", "vpn", "laptop", "wifi", "network", "email",
                "outlook", "software", "install", "printer", "server", "access", "computer", "screen", "mouse",
                "keyboard", "bug", "system", "reset", "account locked", "application", "website", "internet"));
        KEYWORDS.put(Category.HR, List.of("leave", "annual leave", "vacation", "sick", "maternity", "recruitment",
                "hiring", "onboarding", "contract", "performance review", "benefits", "grievance", "disciplinary",
                "training", "resignation", "payslip", "employee", "policy"));
        KEYWORDS.put(Category.FINANCE, List.of("invoice", "payment", "refund", "expense", "reimbursement", "budget",
                "salary", "payroll", "tax", "purchase order", "claim", "receipt", "bank", "quotation", "paid"));
        KEYWORDS.put(Category.OPERATIONS, List.of("delivery", "supplier", "stock", "inventory", "logistics",
                "warehouse", "schedule", "maintenance", "facility", "equipment", "vehicle", "booking",
                "meeting room", "office", "shipment", "supply", "cleaning", "security"));
    }

    @Override
    public ClassificationResult classify(String title, String description) {
        String titleText = normalize(title);
        String fullText = normalize(title + " " + description);

        Map<Category, Integer> scores = new EnumMap<>(Category.class);
        int total = 0;
        for (var entry : KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                if (containsWord(fullText, kw)) score++;
                if (containsWord(titleText, kw)) score++;   // title matches count double
            }
            scores.put(entry.getKey(), score);
            total += score;
        }

        Category best = Category.OPERATIONS;      // default bucket when nothing matches
        int bestScore = 0;
        for (var e : scores.entrySet()) {
            if (e.getValue() > bestScore) { best = e.getKey(); bestScore = e.getValue(); }
        }
        double confidence = total == 0 ? 0.30 : Math.min(0.95, 0.40 + 0.55 * bestScore / total);

        Priority priority = detectPriority(fullText);
        String why = total == 0 ? "No keywords matched; defaulted to OPERATIONS"
                : "Matched " + bestScore + " keyword signal(s) for " + best;
        return new ClassificationResult(best, priority, round2(confidence), "KEYWORD", why);
    }

    private Priority detectPriority(String text) {
        for (String k : URGENT) if (containsWord(text, k)) return Priority.URGENT;
        for (String k : HIGH) if (containsWord(text, k)) return Priority.HIGH;
        for (String k : LOW) if (containsWord(text, k)) return Priority.LOW;
        return Priority.MEDIUM;
    }

    private static String normalize(String s) {
        String cleaned = s == null ? "" : s.toLowerCase().replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ");
        return " " + cleaned.trim() + " ";
    }

    private static boolean containsWord(String paddedText, String kw) {
        return paddedText.contains(" " + kw + " ") || paddedText.contains(" " + kw + "s ");
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
