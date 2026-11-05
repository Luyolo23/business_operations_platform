package com.capaciti.ops.ai;

import org.springframework.stereotype.Component;

@Component
public class TemplateInsightNarrator implements InsightNarrator {
    @Override
    public String narrate(String structuredFacts) {
        return structuredFacts;   // facts are already phrased as sentences by InsightService
    }
}
