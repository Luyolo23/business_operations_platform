package com.capaciti.ops.ai;

/**
 * AI-TEAMMATE SEAM #1. The platform only depends on this interface.
 * Implementations: {@link KeywordTicketClassifier} (default, offline) and {@link LlmTicketClassifier}
 * (active when app.ai.provider=llm). To plug in a trained model, implement this interface and mark it @Primary.
 */
public interface TicketClassifier {
    ClassificationResult classify(String title, String description);
}
