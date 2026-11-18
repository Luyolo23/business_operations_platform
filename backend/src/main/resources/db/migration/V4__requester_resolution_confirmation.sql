-- V4: Let the person who raised a ticket confirm (or reject) a resolution.
-- Staff mark a ticket RESOLVED; the requester can then confirm it (CLOSED) or
-- say it is still a problem (reopened to IN_PROGRESS).

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS requester_confirmed_at TIMESTAMPTZ;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS requester_feedback TEXT;

CREATE INDEX IF NOT EXISTS idx_tickets_requester_confirmed
    ON tickets (requester_confirmed_at);
