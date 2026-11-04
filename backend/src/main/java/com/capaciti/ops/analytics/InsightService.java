package com.capaciti.ops.analytics;

import com.capaciti.ops.analytics.AnalyticsDtos.*;
import com.capaciti.ops.ticket.Category;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Turns numbers into plain-English "weekly summary insights". Deterministic, no AI needed. */
@Service
public class InsightService {

    private final AnalyticsService analytics;

    public InsightService(AnalyticsService analytics) { this.analytics = analytics; }

    public Insights insights(int days, Category category) {
        Summary s = analytics.summary(days, category);
        return new Insights(s, describe(s));
    }

    public static List<String> describe(Summary s) {
        List<String> out = new ArrayList<>();
        String scope = s.categoryFilter() == null ? "across all departments" : "for " + s.categoryFilter();
        out.add("In the last " + s.days() + " days, " + s.totalTickets() + " ticket(s) were raised " + scope + "; "
                + s.resolvedTickets() + " resolved and " + s.openTickets() + " still open.");
        if (s.totalTickets() == 0) return out;

        s.byCategory().entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(top -> {
            if (s.categoryFilter() == null && top.getValue() > 0) {
                long pct = Math.round(100.0 * top.getValue() / s.totalTickets());
                out.add("Busiest category: " + top.getKey() + " with " + top.getValue() + " ticket(s) (" + pct + "%).");
            }
        });
        s.dailyVolume().stream().max(Comparator.comparingLong(DailyCount::count)).ifPresent(peak -> {
            if (peak.count() > 0) out.add("Peak day: " + peak.date() + " with " + peak.count() + " ticket(s).");
        });
        if (s.avgFirstResponseHours() > 0) out.add("Average first response time is " + s.avgFirstResponseHours() + " hours.");
        if (s.avgResolutionHours() > 0) out.add("Average resolution time is " + s.avgResolutionHours() + " hours.");
        if (s.slaBreaches() > 0) out.add(s.slaBreaches() + " ticket(s) breached their SLA ("
                + Math.round(s.slaBreachRate() * 100) + "%).");
        long urgent = s.byPriority().getOrDefault(com.capaciti.ops.ticket.Priority.URGENT, 0L);
        if (urgent > 0) out.add(urgent + " ticket(s) were urgent.");
        return out;
    }
}
