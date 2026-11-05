package com.capaciti.ops.compliance;

import com.capaciti.ops.ticket.Category;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ComplianceDtos {
    private ComplianceDtos() {}

    public record Overview(Map<RiskLevel, Long> responsesByRiskLevel, long pendingApprovals, long approved,
                           long rejected, Map<Category, Double> avgClassificationConfidence,
                           double manualOverrideRate, Map<Category, Double> avgFirstResponseHours,
                           long ticketsConsidered) {}

    public record FlaggedItem(Long assessmentId, Long responseId, Long ticketId, RiskLevel level,
                              List<String> flags, String notes, Instant createdAt) {}

    /** The "risk evaluation report" (Week 6). */
    public record RiskReport(Instant generatedAt, Overview overview, List<FlaggedItem> recentFlagged,
                             List<String> observations) {}

    public record AuditLogDto(Long id, String action, String entityType, Long entityId, String actorEmail,
                              String details, Instant createdAt) {}
}
