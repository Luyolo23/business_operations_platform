package com.capaciti.ops.ai;

/**
 * AI-TEAMMATE SEAM #3. Turns a block of structured facts (numbers already computed by the backend)
 * into an executive-friendly paragraph for the weekly report.
 */
public interface InsightNarrator {
    String narrate(String structuredFacts);
}
