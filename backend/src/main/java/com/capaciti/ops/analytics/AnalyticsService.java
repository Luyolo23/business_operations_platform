package com.capaciti.ops.analytics;

import com.capaciti.ops.analytics.AnalyticsDtos.*;
import com.capaciti.ops.common.AppTime;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketRepository;
import com.capaciti.ops.ticket.TicketStatus;
import com.capaciti.ops.workflow.RoutingRule;
import com.capaciti.ops.workflow.RoutingRuleRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Week 3: trend analysis for the dashboard. Aggregates in Java for readability at bootcamp data volumes. */
@Service
public class AnalyticsService {

    private final TicketRepository tickets;
    private final RoutingRuleRepository rules;

    public AnalyticsService(TicketRepository tickets, RoutingRuleRepository rules) {
        this.tickets = tickets;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public Summary summary(int days, Category category) {
        int window = Math.min(Math.max(days, 1), 365);
        Instant to = Instant.now();
        Instant from = to.minus(window, ChronoUnit.DAYS);
        List<Ticket> list = category == null ? tickets.findByCreatedAtAfter(from)
                : tickets.findByCreatedAtAfterAndCategory(from, category);

        Map<Category, Integer> slaByCategory = new EnumMap<>(Category.class);
        for (RoutingRule r : rules.findAll()) slaByCategory.put(r.getCategory(), r.getSlaHours());

        Map<Category, Long> byCategory = zeroed(Category.values());
        Map<Priority, Long> byPriority = zeroed(Priority.values());
        Map<TicketStatus, Long> byStatus = zeroed(TicketStatus.values());
        long open = 0, resolved = 0, breaches = 0;
        for (Ticket t : list) {
            byCategory.merge(t.getCategory(), 1L, Long::sum);
            byPriority.merge(t.getPriority(), 1L, Long::sum);
            byStatus.merge(t.getStatus(), 1L, Long::sum);
            boolean done = t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED;
            if (done) resolved++; else open++;
            int sla = slaByCategory.getOrDefault(t.getCategory(), 48);
            Instant end = t.getResolvedAt() != null ? t.getResolvedAt() : to;
            if (Duration.between(t.getCreatedAt(), end).toHours() >= sla) breaches++;
        }

        double avgFirst = avgHours(list, t -> t.getFirstResponseAt());
        double avgRes = avgHours(list, t -> t.getResolvedAt());
        Map<Category, Double> firstByCat = new EnumMap<>(Category.class);
        Map<Category, List<Ticket>> grouped = list.stream().collect(Collectors.groupingBy(Ticket::getCategory));
        for (Category c : Category.values()) firstByCat.put(c, avgHours(grouped.getOrDefault(c, List.of()),
                t -> t.getFirstResponseAt()));

        return new Summary(window, category, from, to, list.size(), open, resolved, avgFirst, avgRes, breaches,
                list.isEmpty() ? 0 : Math.round(1000.0 * breaches / list.size()) / 1000.0,
                byCategory, byPriority, byStatus, firstByCat, dailyVolume(list, window));
    }

    /** Zero-filled day-by-day ticket counts, oldest first, ending today (business time zone). */
    public static List<DailyCount> dailyVolume(List<Ticket> list, int days) {
        LocalDate today = LocalDate.now(AppTime.ZONE);
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (int i = days - 1; i >= 0; i--) counts.put(today.minusDays(i), 0L);
        for (Ticket t : list) {
            LocalDate d = t.getCreatedAt().atZone(AppTime.ZONE).toLocalDate();
            if (counts.containsKey(d)) counts.merge(d, 1L, Long::sum);
        }
        List<DailyCount> out = new ArrayList<>();
        counts.forEach((d, c) -> out.add(new DailyCount(d, c)));
        return out;
    }

    private static double avgHours(List<Ticket> list, java.util.function.Function<Ticket, Instant> end) {
        double v = list.stream().filter(t -> end.apply(t) != null)
                .mapToDouble(t -> Duration.between(t.getCreatedAt(), end.apply(t)).toMinutes() / 60.0)
                .average().orElse(0);
        return Math.round(v * 10.0) / 10.0;
    }

    private static <E extends Enum<E>> Map<E, Long> zeroed(E[] values) {
        Map<E, Long> m = new EnumMap<>(values[0].getDeclaringClass());
        for (E e : values) m.put(e, 0L);
        return m;
    }
}
