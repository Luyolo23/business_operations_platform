package com.capaciti.ops.analytics;

import com.capaciti.ops.analytics.AnalyticsDtos.Insights;
import com.capaciti.ops.analytics.AnalyticsDtos.Summary;
import com.capaciti.ops.ticket.Category;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final InsightService insights;

    public AnalyticsController(AnalyticsService analytics, InsightService insights) {
        this.analytics = analytics;
        this.insights = insights;
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(defaultValue = "30") int days,
                           @RequestParam(required = false) Category category) {
        return analytics.summary(days, category);
    }

    @GetMapping("/insights")
    public Insights insights(@RequestParam(defaultValue = "7") int days,
                             @RequestParam(required = false) Category category) {
        return insights.insights(days, category);
    }
}
