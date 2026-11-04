-- Default response templates (category x tone) and routing rules/SLAs.
-- Placeholders: {{requesterName}} {{ticketId}} {{title}} {{priority}} {{assigneeName}}

INSERT INTO response_templates (category, tone, body) VALUES
('HR','FORMAL','Dear {{requesterName}},

Thank you for contacting HR regarding "{{title}}" (Ticket #{{ticketId}}). Your request has been logged with {{priority}} priority and assigned to {{assigneeName}}. Our HR team will review your request and confirm next steps. We will update you as soon as we have more information.

Kind regards,
HR Team'),
('HR','FRIENDLY','Hi {{requesterName}},

Thanks for reaching out to HR! We''ve got your request "{{title}}" (Ticket #{{ticketId}}) and {{assigneeName}} is on it. Our HR team will review your request and confirm next steps. We''ll keep you posted.

Cheers,
HR Team'),
('HR','URGENT','Hi {{requesterName}},

We have received your urgent request "{{title}}" (Ticket #{{ticketId}}) and have flagged it as {{priority}} priority. {{assigneeName}} has been notified and will act on it as a priority. Our HR team will review your request and confirm next steps. Please reply to this ticket if the situation changes.

HR Team'),
('IT','FORMAL','Dear {{requesterName}},

Thank you for contacting IT Support regarding "{{title}}" (Ticket #{{ticketId}}). Your request has been logged with {{priority}} priority and assigned to {{assigneeName}}. Our IT team will investigate the issue and work towards a resolution. We will update you as soon as we have more information.

Kind regards,
IT Support Team'),
('IT','FRIENDLY','Hi {{requesterName}},

Thanks for reaching out to IT Support! We''ve got your request "{{title}}" (Ticket #{{ticketId}}) and {{assigneeName}} is on it. Our IT team will investigate the issue and work towards a resolution. We''ll keep you posted.

Cheers,
IT Support Team'),
('IT','URGENT','Hi {{requesterName}},

We have received your urgent request "{{title}}" (Ticket #{{ticketId}}) and have flagged it as {{priority}} priority. {{assigneeName}} has been notified and will act on it as a priority. Our IT team will investigate the issue and work towards a resolution. Please reply to this ticket if the situation changes.

IT Support Team'),
('FINANCE','FORMAL','Dear {{requesterName}},

Thank you for contacting Finance regarding "{{title}}" (Ticket #{{ticketId}}). Your request has been logged with {{priority}} priority and assigned to {{assigneeName}}. Our Finance team will review the details and verify them against our records. We will update you as soon as we have more information.

Kind regards,
Finance Team'),
('FINANCE','FRIENDLY','Hi {{requesterName}},

Thanks for reaching out to Finance! We''ve got your request "{{title}}" (Ticket #{{ticketId}}) and {{assigneeName}} is on it. Our Finance team will review the details and verify them against our records. We''ll keep you posted.

Cheers,
Finance Team'),
('FINANCE','URGENT','Hi {{requesterName}},

We have received your urgent request "{{title}}" (Ticket #{{ticketId}}) and have flagged it as {{priority}} priority. {{assigneeName}} has been notified and will act on it as a priority. Our Finance team will review the details and verify them against our records. Please reply to this ticket if the situation changes.

Finance Team'),
('OPERATIONS','FORMAL','Dear {{requesterName}},

Thank you for contacting Operations regarding "{{title}}" (Ticket #{{ticketId}}). Your request has been logged with {{priority}} priority and assigned to {{assigneeName}}. Our Operations team will look into this and coordinate the necessary follow-up. We will update you as soon as we have more information.

Kind regards,
Operations Team'),
('OPERATIONS','FRIENDLY','Hi {{requesterName}},

Thanks for reaching out to Operations! We''ve got your request "{{title}}" (Ticket #{{ticketId}}) and {{assigneeName}} is on it. Our Operations team will look into this and coordinate the necessary follow-up. We''ll keep you posted.

Cheers,
Operations Team'),
('OPERATIONS','URGENT','Hi {{requesterName}},

We have received your urgent request "{{title}}" (Ticket #{{ticketId}}) and have flagged it as {{priority}} priority. {{assigneeName}} has been notified and will act on it as a priority. Our Operations team will look into this and coordinate the necessary follow-up. Please reply to this ticket if the situation changes.

Operations Team');

INSERT INTO routing_rules (category, target_department, sla_hours, active) VALUES
 ('HR','HR',48,TRUE),
 ('IT','IT',24,TRUE),
 ('FINANCE','FINANCE',48,TRUE),
 ('OPERATIONS','OPERATIONS',72,TRUE);
