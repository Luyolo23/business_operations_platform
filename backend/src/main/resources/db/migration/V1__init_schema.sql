-- Core schema for the AI Business Operations Assistant.
-- Owned by the DATABASE teammate. Change the schema only by adding new V<n>__*.sql files.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,          -- EMPLOYEE | AGENT | MANAGER | ADMIN
    department    VARCHAR(30),                    -- HR | IT | FINANCE | OPERATIONS (agents only)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE routing_rules (
    id                BIGSERIAL PRIMARY KEY,
    category          VARCHAR(30) NOT NULL UNIQUE,
    target_department VARCHAR(30) NOT NULL,
    sla_hours         INT         NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE tickets (
    id                        BIGSERIAL PRIMARY KEY,
    title                     VARCHAR(255) NOT NULL,
    description               TEXT         NOT NULL,
    requester_id              BIGINT       NOT NULL REFERENCES users(id),
    category                  VARCHAR(30)  NOT NULL,
    priority                  VARCHAR(30)  NOT NULL,
    status                    VARCHAR(30)  NOT NULL,
    assignee_id               BIGINT       REFERENCES users(id),
    classification_confidence DOUBLE PRECISION,
    classification_source     VARCHAR(100),       -- KEYWORD | LLM:<model> | MANUAL ...
    escalated                 BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    first_response_at         TIMESTAMPTZ,
    resolved_at               TIMESTAMPTZ
);
CREATE INDEX idx_tickets_created_at ON tickets(created_at);
CREATE INDEX idx_tickets_category   ON tickets(category);
CREATE INDEX idx_tickets_status     ON tickets(status);
CREATE INDEX idx_tickets_assignee   ON tickets(assignee_id);
CREATE INDEX idx_tickets_requester  ON tickets(requester_id);

CREATE TABLE response_templates (
    id       BIGSERIAL PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    tone     VARCHAR(30) NOT NULL,                -- FORMAL | FRIENDLY | URGENT
    body     TEXT        NOT NULL,
    UNIQUE (category, tone)
);

CREATE TABLE ticket_responses (
    id                BIGSERIAL PRIMARY KEY,
    ticket_id         BIGINT      NOT NULL REFERENCES tickets(id),
    tone              VARCHAR(30) NOT NULL,
    body              TEXT        NOT NULL,
    generated_by      VARCHAR(100) NOT NULL,      -- template | llm:<model>
    transparency_note TEXT,
    status            VARCHAR(30) NOT NULL,       -- PENDING_APPROVAL | APPROVED | REJECTED
    risk_level        VARCHAR(30) NOT NULL,       -- NONE | LOW | MEDIUM | HIGH
    created_by        BIGINT      REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_responses_ticket ON ticket_responses(ticket_id);

CREATE TABLE approvals (
    id               BIGSERIAL PRIMARY KEY,
    response_id      BIGINT      NOT NULL REFERENCES ticket_responses(id),
    status           VARCHAR(30) NOT NULL,        -- PENDING | APPROVED | REJECTED
    decided_by       BIGINT      REFERENCES users(id),
    decision_comment TEXT,
    decided_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_approvals_status ON approvals(status);

CREATE TABLE risk_assessments (
    id          BIGSERIAL PRIMARY KEY,
    response_id BIGINT      NOT NULL REFERENCES ticket_responses(id),
    level       VARCHAR(30) NOT NULL,
    flags       TEXT,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   BIGINT,
    actor_email VARCHAR(255),
    details     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    ticket_id  BIGINT,
    subject    VARCHAR(255) NOT NULL,
    message    TEXT         NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);
