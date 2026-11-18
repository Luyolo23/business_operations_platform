-- V5: enforce the ticket state machine at the API level and add optimistic locking.
-- Existing rows start at version 0; Hibernate increments on every update.
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ticket_responses
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE approvals
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- One approval per response. This prevents two managers from creating competing
-- approval rows for the same drafted reply.
CREATE UNIQUE INDEX IF NOT EXISTS uq_approvals_response_id
    ON approvals (response_id);
