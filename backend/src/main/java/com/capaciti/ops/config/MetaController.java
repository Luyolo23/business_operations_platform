package com.capaciti.ops.config;

import com.capaciti.ops.response.Tone;
import com.capaciti.ops.ticket.Category;
import com.capaciti.ops.ticket.Priority;
import com.capaciti.ops.ticket.TicketStatus;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the frontend build dropdowns from the backend's enums instead of hard-coding them. */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of(
                "categories", Category.values(),
                "priorities", Priority.values(),
                "statuses", TicketStatus.values(),
                "tones", Tone.values());
    }
}
