package com.capaciti.ops.analytics;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record DailyCount(LocalDate date, long count) {}

    public record Summary(int days, Category categoryFilter, Instant from, Instant to, long totalTickets,
                          long openTickets, long resolvedTickets, double avgFirstResponseHours,
                          double avgResolutionHours, long slaBreaches, double slaBreachRate,
                          Map<Category, Long> byCategory, Map<Priority, Long> byPriority,
                          Map<TicketStatus, Long> byStatus, Map<Category, Double> avgFirstResponseHoursByCategory,
                          List<DailyCount> dailyVolume) {}

    public record Insights(Summary summary, List<String> insights) {}
}
