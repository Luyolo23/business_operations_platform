package com.capaciti.ops.compliance;

import com.capaciti.ops.common.PageResponse;
import com.capaciti.ops.compliance.ComplianceDtos.*;
import com.capaciti.ops.response.ApprovalRepository;
import com.capaciti.ops.response.ApprovalStatus;
import com.capaciti.ops.response.TicketResponse;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Week 6: risk assessment of responses, fairness/consistency metrics and the risk evaluation report. */
@Service
public class ComplianceService {

    private final BiasChecker biasChecker;
    private final RiskAssessmentRepository assessments;
    private final AuditLogRepository auditLogs;
    private final ApprovalRepository approvals;
    private final TicketRepository tickets;

    public ComplianceService(BiasChecker biasChecker, RiskAssessmentRepository assessments,
                             AuditLogRepository auditLogs, ApprovalRepository approvals, TicketRepository tickets) {
        this.biasChecker = biasChecker;
        this.assessments = assessments;
        this.auditLogs = auditLogs;
        this.approvals = approvals;
        this.tickets = tickets;
    }

    @Transactional
    public RiskAssessment assess(TicketResponse response) {
        RiskCheckResult result = biasChecker.check(response.getBody());
        RiskAssessment a = new RiskAssessment();
        a.setResponse(response);
        a.setLevel(result.level());
        a.setFlags(String.join(",", result.flags()));
        a.setNotes(result.notes());
        return assessments.save(a);
    }

    @Transactional(readOnly = true)
    public Map<Long, RiskAssessment> latestByResponse(Collection<TicketResponse> responses) {
        if (responses.isEmpty()) return Map.of();
        List<Long> ids = responses.stream().map(TicketResponse::getId).toList();
        return assessments.findByResponseIdIn(ids).stream().collect(Collectors.toMap(
                x -> x.getResponse().getId(), x -> x, (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    @Transactional(readOnly = true)
    public Overview overview(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Ticket> recent = tickets.findByCreatedAtAfter(since);   // bootcamp scale; move to SQL aggregates if needed

        Map<RiskLevel, Long> byLevel = new EnumMap<>(RiskLevel.class);
        for (RiskLevel l : RiskLevel.values()) byLevel.put(l, assessments.countByLevel(l));

        Map<Category, List<Ticket>> byCategory = recent.stream().collect(Collectors.groupingBy(Ticket::getCategory));
        Map<Category, Double> confidence = new EnumMap<>(Category.class);
        Map<Category, Double> firstResponse = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            List<Ticket> list = byCategory.getOrDefault(c, List.of());
            confidence.put(c, round1(list.stream().filter(t -> t.getClassificationConfidence() != null)
                    .mapToDouble(Ticket::getClassificationConfidence).average().orElse(0)));
            firstResponse.put(c, round1(list.stream().filter(t -> t.getFirstResponseAt() != null)
                    .mapToDouble(t -> Duration.between(t.getCreatedAt(), t.getFirstResponseAt()).toMinutes() / 60.0)
                    .average().orElse(0)));
        }
        long manual = recent.stream().filter(t -> "MANUAL".equals(t.getClassificationSource())).count();
        double manualRate = recent.isEmpty() ? 0 : Math.round(1000.0 * manual / recent.size()) / 1000.0;

        return new Overview(byLevel, approvals.countByStatus(ApprovalStatus.PENDING),
                approvals.countByStatus(ApprovalStatus.APPROVED), approvals.countByStatus(ApprovalStatus.REJECTED),
                confidence, manualRate, firstResponse, recent.size());
    }

    @Transactional(readOnly = true)
    public RiskReport report(int days) {
        Overview o = overview(days);
        List<FlaggedItem> flagged = assessments
                .findTop20ByLevelInOrderByCreatedAtDesc(List.of(RiskLevel.MEDIUM, RiskLevel.HIGH)).stream()
                .map(a -> new FlaggedItem(a.getId(), a.getResponse().getId(), a.getResponse().getTicket().getId(),
                        a.getLevel(), a.getFlags() == null || a.getFlags().isBlank() ? List.of()
                        : Arrays.asList(a.getFlags().split(",")), a.getNotes(), a.getCreatedAt()))
                .toList();

        List<String> obs = new ArrayList<>();
        long high = o.responsesByRiskLevel().getOrDefault(RiskLevel.HIGH, 0L);
        long medium = o.responsesByRiskLevel().getOrDefault(RiskLevel.MEDIUM, 0L);
        if (high > 0) obs.add(high + " response(s) were rated HIGH risk and required human review.");
        if (medium > 0) obs.add(medium + " response(s) were rated MEDIUM risk.");
        if (o.manualOverrideRate() > 0.2) obs.add("Staff overrode " + Math.round(o.manualOverrideRate() * 100)
                + "% of AI classifications - the classifier may need retuning.");
        double slowest = o.avgFirstResponseHours().values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double fastest = o.avgFirstResponseHours().values().stream().filter(v -> v > 0)
                .mapToDouble(Double::doubleValue).min().orElse(0);
        if (fastest > 0 && slowest > 2 * fastest) obs.add("First-response time varies by more than 2x between "
                + "categories (" + fastest + "h vs " + slowest + "h); check the service is consistent for all requesters.");
        if (obs.isEmpty()) obs.add("No material risk indicators in the selected period.");
        return new RiskReport(Instant.now(), o, flagged, obs);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> audit(int page, int size) {
        var p = auditLogs.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(p, a -> new AuditLogDto(a.getId(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getActorEmail(), a.getDetails(), a.getCreatedAt()));
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
