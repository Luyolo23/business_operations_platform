-- V3: Schema hardening.
-- Add active column to users, tighten indexes and add missing FK on notifications.
-- Do NOT edit V1 or V2 (Flyway checksums are locked).

-- 1. Soft-delete support: users can be deactivated without losing ticket history
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Case-insensitive unique email index
--    The application already queries with findByEmailIgnoreCase; this index
--    prevents duplicate accounts that differ only by case (A@x.com vs a@x.com).
CREATE UNIQUE INDEX IF NOT EXISTS uidx_users_email_lower
    ON users (lower(email));

-- 3. Missing FK on notifications.ticket_id
--    (the original schema left this as an un-constrained BIGINT)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_notifications_ticket'
    ) THEN
        ALTER TABLE notifications
            ADD CONSTRAINT fk_notifications_ticket
            FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 4. Composite indexes for the most common list queries
CREATE INDEX IF NOT EXISTS idx_tickets_status_created
    ON tickets (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tickets_requester_created
    ON tickets (requester_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_tickets_assignee_status
    ON tickets (assignee_id, status);
