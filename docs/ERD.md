
# Entity Relationship Diagram

Schema is owned by Flyway migrations in `backend/src/main/resources/db/migration/`.
Hibernate never changes it (`spring.jpa.hibernate.ddl-auto: none`).

```mermaid
erDiagram
    users ||--o{ tickets : "raises (requester)"
    users ||--o{ tickets : "handles (assignee)"
    users ||--o{ ticket_responses : "writes"
    users ||--o{ approvals : "decides"
    users ||--o{ notifications : "receives"

    tickets ||--o{ ticket_responses : "has"
    tickets ||--o{ notifications : "about"
    routing_rules ||--|| tickets : "routes by category"
    response_templates ||--|| tickets : "drafts by category + tone"

    ticket_responses ||--o{ risk_assessments : "screened by"
    ticket_responses ||--o| approvals : "held by (at most one)"

    users {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar role "EMPLOYEE | AGENT | MANAGER | ADMIN"
        varchar department "HR | IT | FINANCE | OPERATIONS"
        boolean active
        timestamptz created_at
    }
    tickets {
        bigint id PK
        varchar title
        text description
        bigint requester_id FK
        bigint assignee_id FK
        varchar category
        varchar priority
        varchar status
        double classification_confidence
        varchar classification_source
        boolean escalated
        timestamptz created_at
        timestamptz first_response_at
        timestamptz resolved_at
        timestamptz requester_confirmed_at
        text requester_feedback
    }
    ticket_responses {
        bigint id PK
        bigint ticket_id FK
        varchar tone
        text body
        varchar generated_by
        text transparency_note
        varchar status "PENDING_APPROVAL | APPROVED | REJECTED"
        varchar risk_level "NONE | LOW | MEDIUM | HIGH"
        bigint created_by FK
        timestamptz created_at
    }
    approvals {
        bigint id PK
        bigint response_id FK,UK
        varchar status "PENDING | APPROVED | REJECTED"
        bigint decided_by FK
        text decision_comment
        timestamptz decided_at
        timestamptz created_at
    }
    risk_assessments {
        bigint id PK
        bigint response_id FK
        varchar level "NONE | LOW | MEDIUM | HIGH"
        text flags
        text notes
        timestamptz created_at
    }
    notifications {
        bigint id PK
        bigint user_id FK
        bigint ticket_id FK
        varchar subject
        text message
        boolean is_read
        timestamptz created_at
    }
    audit_logs {
        bigint id PK
        varchar action
        varchar entity_type
        bigint entity_id
        varchar actor_email
        text details
        timestamptz created_at
    }
    routing_rules {
        bigint id PK
        varchar category UK
        varchar target_department
        int sla_hours
        boolean active
    }
    response_templates {
        bigint id PK
        varchar category
        varchar tone
        text body
    }
```

## Migration history

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__init_schema.sql` | core tables and base indexes |
| V2 | `V2__seed_templates_and_rules.sql` | 12 response templates + 4 routing rules |
| V3 | `V3__schema_improvements.sql` | `users.active`, case-insensitive email, composite indexes |
| V4 | `V4__requester_resolution_confirmation.sql` | requester confirmation columns + index |
| V5 | `V5__ticket_state_and_optimistic_locking.sql` | optimistic locking, unique approval per response |
| V6 | `V6__hot_path_indexes.sql` | reporting/approval/notification indexes |
| V7 | `V7__enum_check_constraints.sql` | CHECK constraints for enum columns |

## Key invariants

1. One response can have **at most one** approval (unique index `uq_approvals_response_id`).
2. A ticket can have **at most one pending approval** at a time (enforced in `ResponseService`).
3. Risk assessments are append-only; the newest row per response is the effective level.
4. `approvals.decided_at` is null until a manager decides.
5. Every write path is audited in `audit_logs`.

## Index rationale

| Index | Backs |
|-------|-------|
| `idx_tickets_status_created` | ticket list filtered by status, newest first |
| `idx_tickets_requester_created` | an employee's own ticket list |
| `idx_responses_ticket` | the reply thread on a ticket detail page |
| `idx_approvals_status_created_at` | approvals list per status |
| `idx_approvals_decided_at` | approved/rejected counts in compliance report |
| `idx_assessments_created_at` | risk report window |
| `idx_notifications_user_created_at` | unread badge poll every 30 s |
| `idx_tickets_status_escalated` | SLA escalation sweep every 15 min |
