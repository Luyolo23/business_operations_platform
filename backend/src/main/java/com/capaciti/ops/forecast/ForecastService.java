package com.capaciti.ops.forecast;

import com.capaciti.ops.analytics.AnalyticsDtos.DailyCount;
import com.capaciti.ops.analytics.AnalyticsService;
import com.capaciti.ops.common.AppTime;
import com.capaciti.ops.forecast.ForecastDtos.*;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Ticket;
import com.capaciti.ops.ticket.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Week 5: simple, explainable forecasting - linear trend over daily ticket volume. */
@Service
public class ForecastService {

    static final int HISTORY_DAYS = 28;
    private final TicketRepository tickets;

    public ForecastService(TicketRepository tickets) { this.tickets = tickets; }

    @Transactional(readOnly = true)
    public Forecast forecast(int horizonDays) {
        int horizon = Math.min(Math.max(horizonDays, 1), 30);
        List<Ticket> history = tickets.findByCreatedAtAfter(Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS));
        List<DailyCount> daily = AnalyticsService.dailyVolume(history, HISTORY_DAYS);

        double[] y = daily.stream().mapToDouble(DailyCount::count).toArray();
        LinearTrend trend = LinearTrend.fit(y);
        double histAvg = round1(java.util.Arrays.stream(y).average().orElse(0));

        LocalDate last = LocalDate.now(AppTime.ZONE);
        List<ProjectedDay> projection = new ArrayList<>();
        double total = 0;
        for (int i = 1; i <= horizon; i++) {
            double predicted = Math.max(0, trend.predict(y.length - 1 + i));
            projection.add(new ProjectedDay(last.plusDays(i), round1(predicted)));
            total += predicted;
        }
        double projAvg = round1(total / horizon);
        boolean surge = histAvg > 0 && projAvg > histAvg * 1.25;

        // Projected workload per category: recent (14d) category share x projected total.
        Map<Category, Long> recentCounts = history.stream()
                .filter(t -> t.getCreatedAt().isAfter(Instant.now().minus(14, ChronoUnit.DAYS)))
                .collect(Collectors.groupingBy(Ticket::getCategory, Collectors.counting()));
        long recentTotal = recentCounts.values().stream().mapToLong(Long::longValue).sum();
        Map<Category, Double> byCategory = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            double share = recentTotal == 0 ? 0 : (double) recentCounts.getOrDefault(c, 0L) / recentTotal;
            byCategory.put(c, round1(total * share));
        }

        String message = history.isEmpty()
                ? "Not enough historical data to forecast yet."
                : surge ? "Expect a surge: about " + projAvg + " tickets/day vs a " + histAvg + "/day average. Consider extra capacity."
                : "Workload looks stable: about " + projAvg + " tickets/day expected (average " + histAvg + "/day).";

        return new Forecast("LINEAR_TREND_28D", HISTORY_DAYS, horizon, histAvg, projAvg, round1(total), surge,
                message, daily, projection, byCategory);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
