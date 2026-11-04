package com.capaciti.ops.ai;

import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;

/** Output of any ticket classifier. {@code source} is stored on the ticket for transparency. */
public record ClassificationResult(Category category, Priority priority, double confidence, String source,
                                   String rationale) {
}
