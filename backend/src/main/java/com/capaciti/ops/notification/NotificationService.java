package com.capaciti.ops.notification;

import com.capaciti.ops.common.Exceptions.ForbiddenException;
import com.capaciti.ops.common.Exceptions.NotFoundException;
import com.capaciti.ops.security.AuthUser;
import com.capaciti.ops.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final EmailSender emailSender;

    public NotificationService(NotificationRepository repo, EmailSender emailSender) {
        this.repo = repo;
        this.emailSender = emailSender;
    }

    /** Stores an in-app notification and (simulated) emails the user. */
    @Transactional
    public void notify(User user, Long ticketId, String subject, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setTicketId(ticketId);
        n.setSubject(subject);
        n.setMessage(message);
        repo.save(n);
        emailSender.send(user.getEmail(), subject, message);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> mine(AuthUser caller) {
        return repo.findTop50ByUserIdOrderByCreatedAtDesc(caller.id()).stream().map(NotificationDto::from).toList();
    }

    @Transactional
    public NotificationDto markRead(Long id, AuthUser caller) {
        Notification n = repo.findById(id).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(caller.id())) throw new ForbiddenException("Not your notification");
        n.setRead(true);
        return NotificationDto.from(n);
    }
}
