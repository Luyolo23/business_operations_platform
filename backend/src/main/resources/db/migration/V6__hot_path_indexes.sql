CREATE INDEX IF NOT EXISTS idx_assessments_created_at
    ON risk_assessments(created_at);

-- UserController counts responses written by a user before deleting the account.
CREATE INDEX IF NOT EXISTS idx_responses_created_by
    ON ticket_responses(created_by);

-- ApprovalService.list() and countByStatus* order/filter by status then time.
CREATE INDEX IF NOT EXISTS idx_approvals_status_created_at
    ON approvals(status, created_at);

-- ComplianceService counts approved/rejected decisions inside a date window.
CREATE INDEX IF NOT EXISTS idx_approvals_decided_at
    ON approvals(decided_at);

-- Layout.tsx polls the newest notifications for one user every 30 seconds.
CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at
    ON notifications(user_id, created_at DESC);

-- WorkflowService.escalateOverdue() selects open, not-yet-escalated tickets.
CREATE INDEX IF NOT EXISTS idx_tickets_status_escalated
    ON tickets(status, escalated);