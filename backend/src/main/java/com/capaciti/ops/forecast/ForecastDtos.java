package com.capaciti.ops.forecast;

import com.capaciti.ops.analytics.AnalyticsDtos.DailyCount;
import com.capaciti.ops.ticket.Category;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ForecastDtos {
    private ForecastDtos() {}

    public record ProjectedDay(LocalDate date, double predictedTickets) {}

    public record Forecast(String method, int historyDays, int horizonDays, double historicalDailyAverage,
                           double projectedDailyAverage, double projectedTotal, boolean surgeExpected,
                           String message, List<DailyCount> history, List<ProjectedDay> projection,
                           Map<Category, Double> projectedByCategory) {}
}
