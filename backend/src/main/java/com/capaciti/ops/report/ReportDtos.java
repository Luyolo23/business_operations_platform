package com.capaciti.ops.report;

import com.capaciti.ops.analytics.AnalyticsDtos.Summary;
import com.capaciti.ops.forecast.ForecastDtos.Forecast;
import com.capaciti.ops.ticket.Category;
import java.time.Instant;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    public record WeeklyReport(Instant generatedAt, Category department, String title, String narrative,
                               List<String> insights, Summary summary, Forecast forecast) {}
}
