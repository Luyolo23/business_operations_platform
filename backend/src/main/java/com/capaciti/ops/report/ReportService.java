package com.capaciti.ops.report;

import com.capaciti.ops.ai.InsightNarrator;
import com.capaciti.ops.analytics.AnalyticsDtos.Summary;
import com.capaciti.ops.analytics.AnalyticsService;
import com.capaciti.ops.analytics.InsightService;
import com.capaciti.ops.forecast.ForecastDtos.Forecast;
import com.capaciti.ops.forecast.ForecastService;
import com.capaciti.ops.report.ReportDtos.WeeklyReport;
import com.capaciti.ops.ticket.Category;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Week 4: automated weekly business report, optionally filtered per department. */
@Service
public class ReportService {

    private final AnalyticsService analytics;
    private final ForecastService forecastService;
    private final InsightNarrator narrator;
    private final PdfReportGenerator pdf;

    public ReportService(AnalyticsService analytics, ForecastService forecastService,
                         InsightNarrator narrator, PdfReportGenerator pdf) {
        this.analytics = analytics;
        this.forecastService = forecastService;
        this.narrator = narrator;
        this.pdf = pdf;
    }

    public WeeklyReport weekly(Category department) {
        Summary summary = analytics.summary(7, department);
        List<String> insights = InsightService.describe(summary);
        Forecast forecast = forecastService.forecast(7);
        String facts = String.join(" ", insights) + " Forecast: " + forecast.message();
        String title = "Weekly Business Report" + (department == null ? "" : " - " + department);
        return new WeeklyReport(Instant.now(), department, title, narrator.narrate(facts), insights, summary, forecast);
    }

    public byte[] weeklyPdf(Category department) {
        return pdf.generate(weekly(department));
    }
}
