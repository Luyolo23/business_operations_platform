package com.capaciti.ops.notification;

import java.time.Instant;

public record NotificationDto(Long id, Long ticketId, String subject, String message, boolean read, Instant createdAt) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getTicketId(), n.getSubject(), n.getMessage(), n.isRead(),
                n.getCreatedAt());
    }
}
