package com.capaciti.ops.notification;

import com.capaciti.ops.security.AuthUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public List<NotificationDto> mine(@AuthenticationPrincipal AuthUser caller) { return service.mine(caller); }

    @PostMapping("/{id}/read")
    public NotificationDto read(@PathVariable Long id, @AuthenticationPrincipal AuthUser caller) {
        return service.markRead(id, caller);
    }
}
