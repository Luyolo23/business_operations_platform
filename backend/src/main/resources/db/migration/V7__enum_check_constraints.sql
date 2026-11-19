-- Enforce the enum vocabularies at the database boundary.
-- Values mirror the Java enums in com.capaciti.ops.ticket / response / compliance / user.

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_status
    CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_APPROVAL', 'RESOLVED', 'CLOSED'));

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_priority
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_category
    CHECK (category IN ('HR', 'IT', 'FINANCE', 'OPERATIONS'));

ALTER TABLE ticket_responses
    ADD CONSTRAINT chk_responses_status
    CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'));

ALTER TABLE ticket_responses
    ADD CONSTRAINT chk_responses_risk_level
    CHECK (risk_level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE ticket_responses
    ADD CONSTRAINT chk_responses_tone
    CHECK (tone IN ('FORMAL', 'FRIENDLY', 'URGENT'));

ALTER TABLE approvals
    ADD CONSTRAINT chk_approvals_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE risk_assessments
    ADD CONSTRAINT chk_assessments_level
    CHECK (level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
    CHECK (role IN ('EMPLOYEE', 'AGENT', 'MANAGER', 'ADMIN'));