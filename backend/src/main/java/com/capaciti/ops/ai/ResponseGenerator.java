package com.capaciti.ops.ai;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Ticket;

/**
 * AI-TEAMMATE SEAM #2. Called inside a transaction, so lazy associations on the ticket are safe to read.
 * {@code templateBody} is the stored template for (category, tone) and may be used as a guide or ignored.
 */
public interface ResponseGenerator {
    GeneratedResponse generate(Ticket ticket, Tone tone, String templateBody);
}
