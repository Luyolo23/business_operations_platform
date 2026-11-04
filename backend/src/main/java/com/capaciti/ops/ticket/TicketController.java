package com.capaciti.ops.ticket;

import com.capaciti.ops.common.PageResponse;
import com.capaciti.ops.response.ResponseDtos.GenerateResponseRequest;
import com.capaciti.ops.response.ResponseDtos.ResponseDto;
import com.capaciti.ops.response.ResponseService;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.ticket.TicketDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService tickets;
    private final ResponseService responses;

    public TicketController(TicketService tickets, ResponseService responses) {
        this.tickets = tickets;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDto create(@Valid @RequestBody CreateTicketRequest req, @AuthenticationPrincipal AuthUser caller) {
        return tickets.create(req, caller);
    }

    @GetMapping
    public PageResponse<TicketDto> list(@RequestParam(required = false) Category category,
                                        @RequestParam(required = false) TicketStatus status,
                                        @RequestParam(required = false) Priority priority,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @AuthenticationPrincipal AuthUser caller) {
        return tickets.list(category, status, priority, page, size, caller);
    }

    @GetMapping("/{id}")
    public TicketDto get(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        return tickets.get(id, caller);
    }

    @PatchMapping("/{id}/status")
    public TicketDto status(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req,
                            @AuthenticationPrincipal AuthUser caller) {
        return tickets.updateStatus(id, req.status(), caller);
    }

    @PatchMapping("/{id}/classification")
    public TicketDto reclassify(@PathVariable Long id, @Valid @RequestBody UpdateClassificationRequest req,
                                @AuthenticationPrincipal AuthUser caller) {
        return tickets.reclassify(id, req.category(), req.priority(), caller);
    }

    @PatchMapping("/{id}/assignee")
    public TicketDto assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req,
                            @AuthenticationPrincipal AuthUser caller) {
        return tickets.assign(id, req.userId(), caller);
    }

    @GetMapping("/{id}/responses")
    public List<ResponseDto> listResponses(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        return responses.list(id, caller);
    }

    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto generateResponse(@PathVariable Long id, @Valid @RequestBody GenerateResponseRequest req,
                                        @AuthenticationPrincipal AuthUser caller) {
        return responses.generate(id, req.tone(), caller);
    }
}
