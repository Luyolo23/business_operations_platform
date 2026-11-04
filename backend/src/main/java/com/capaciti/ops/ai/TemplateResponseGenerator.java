package com.capaciti.ops.ai;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Ticket;
import org.springframework.stereotype.Component;

/** Fills the stored template placeholders. Default generator and LLM fallback. */
@Component
public class TemplateResponseGenerator implements ResponseGenerator {

    @Override
    public GeneratedResponse generate(Ticket t, Tone tone, String templateBody) {
        String assignee = t.getAssignee() != null ? t.getAssignee().getFullName() : "our team";
        String body = templateBody
                .replace("{{requesterName}}", firstName(t.getRequester().getFullName()))
                .replace("{{ticketId}}", String.valueOf(t.getId()))
                .replace("{{title}}", t.getTitle())
                .replace("{{priority}}", t.getPriority().name().toLowerCase())
                .replace("{{assigneeName}}", assignee);
        String note = "Generated from the stored " + t.getCategory() + "/" + tone
                + " template. No generative model was used.";
        return new GeneratedResponse(body, "template", note);
    }

    static String firstName(String fullName) {
        int i = fullName.indexOf(' ');
        return i > 0 ? fullName.substring(0, i) : fullName;
    }
}
