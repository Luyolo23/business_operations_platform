package com.capaciti.ops.notification;

/** Extension point: swap LoggingEmailSender for a real SMTP / API sender when you need real emails. */
public interface EmailSender {
    void send(String toEmail, String subject, String body);
}
