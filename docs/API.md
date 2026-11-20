# API Reference

Base URL (local dev): `http://localhost:8080`  
All endpoints except Auth, `/api/config` and the Swagger UI require
`Authorization: Bearer <token>`.  
Full interactive docs: **http://localhost:8080/swagger-ui.html** (available only
with the `dev` profile; production keeps Swagger and `/v3/api-docs` disabled).

---

## Authentication  `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | None | Self-register as EMPLOYEE. Body: `{fullName, email, password}` |
| `POST` | `/api/auth/login` | None | Login. Body: `{email, password}`. Returns `{token, expiresInSeconds, user}` |
| `GET`  | `/api/auth/me` | Any | Returns the currently authenticated user |

---

## Public config  `/api/config`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/config` | None | Safe, token-free settings for the login screen. Returns `{demoMode: boolean}`. |

`demoMode` is backed by `app.demo.show-accounts` (default `false`; the `dev` profile
sets it to `true`). The frontend hides the demo-account pills unless it is `true`.
Only add values here that are safe for an unauthenticated caller to read.

---

## Metadata  `/api/meta`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/meta` | Any | Current enum values for categories, priorities, statuses and tones. |

Used by the frontend to build dropdowns instead of hard-coding enum values.

---

## Tickets  `/api/tickets`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/tickets` | Any | Create a ticket. Body: `{title, description}`. Auto-classifies and routes. |
| `GET`  | `/api/tickets` | Any | List tickets (paginated). Query: `?category=IT&status=ASSIGNED&page=0&size=20` |
| `GET`  | `/api/tickets/{id}` | Any | Get a single ticket (employees see only their own) |
| `PATCH`| `/api/tickets/{id}/status` | AGENT+ | Resolve a ticket or reopen a `RESOLVED` one. Body: `{status}`. Only `RESOLVED` (after a reply has been sent) and `IN_PROGRESS` (reopening `RESOLVED`) are accepted; other targets return `409`. |
| `PATCH`| `/api/tickets/{id}/classification` | AGENT+ | Re-classify. Body: `{category, priority}`. Changing the category re-routes the ticket to the new department. `CLOSED` tickets are refused with `409`. |
| `PATCH`| `/api/tickets/{id}/assignee` | MANAGER+ | Reassign. Body: `{userId}` |
| `POST` | `/api/tickets/{id}/resolution` | Requester | Confirm or reject the fix. Body: `{confirmed, comment?}` |
| `GET`  | `/api/tickets/{id}/responses` | Any | Staff see every response including drafts and risk details. The requester sees `APPROVED` replies only, with internal risk/approval fields removed. |
| `POST` | `/api/tickets/{id}/responses` | AGENT+ | Draft a reply. Body: `{tone, body?}`. Omit `body` for an AI/template draft; send `body` for a manually written reply. Both are risk-screened. |

### Classification

With `AI_PROVIDER=mock` (the default), `KeywordTicketClassifier` scores the title and
description against a per-category vocabulary. Title matches count double. The winning
category is the highest score; if nothing matches, the ticket defaults to `OPERATIONS`
with `confidence = 0.30`. Otherwise confidence is `0.40 + 0.55 × (best score ÷ total score)`,
capped at `0.95`.

| Category | Example vocabulary |
|----------|-------------------|
| IT | password, login, vpn, laptop, wifi, email, printer, server |
| HR | leave, sick, maternity, recruitment, payslip, contract, grievance |
| FINANCE | invoice, payment, refund, expense, salary, payroll, tax, loan, salary advance |
| OPERATIONS | delivery, supplier, stock, inventory, warehouse, maintenance, booking |

Priority comes from separate keyword lists: `URGENT` (urgent, asap, outage, cannot work),
`HIGH` (not working, broken, blocked, unpaid, failed), `LOW` (question, how do i,
information), otherwise `MEDIUM`.

When `AI_PROVIDER=llm`, the model returns the category, priority, confidence and a
one-sentence rationale; invalid output falls back to the keyword classifier.

### Replies and resolution

`POST /api/tickets/{id}/responses` accepts a `tone` (`FORMAL`, `FRIENDLY`, `URGENT`) and an optional `body`:

- **No `body`** — the configured generator drafts the text from the category/tone template.
- **With `body`** — the text is normalised before it is stored (`generatedBy: "manual"`): Unicode NFKC, control/zero-width characters removed, surrounding whitespace trimmed, and a hard limit of 10,000 characters. It is still passed through the same risk checker.

In both cases the reply is normalised before screening, so invisible characters, full-width letters, or embedded NULs cannot hide risky wording. The risk level then decides what happens next:

| Risk | Result |
|---|---|
| `NONE` / `LOW` | Auto-approved, sent to the requester. The ticket moves to `IN_PROGRESS` (from `NEW` or `ASSIGNED`) and `firstResponseAt` is stamped. |
| `MEDIUM` / `HIGH` | Held as `PENDING_APPROVAL`, an `Approval` is created, managers are notified, and the ticket moves to `AWAITING_APPROVAL`. A risky first reply performs `NEW`/`ASSIGNED → IN_PROGRESS → AWAITING_APPROVAL` in one transaction, so work is recorded as started even though the reply is held. |

Drafting is refused with `409` when the ticket is `RESOLVED` or `CLOSED` (reopen it first) or when any reply on the ticket is already waiting for a manager decision.

### Ticket state machine

Every status change goes through one table. Anything not listed returns `409 Conflict`; a ticket may only reach `RESOLVED` after `firstResponseAt` is set, otherwise `400`.

| From | To | Trigger |
|---|---|---|
| `NEW` | `ASSIGNED` | Routing or manual assignment |
| `NEW` | `IN_PROGRESS` | Staff reply (not `PATCH /status`) |
| `ASSIGNED` | `IN_PROGRESS` | Staff reply |
| `IN_PROGRESS` | `AWAITING_APPROVAL` | A risky reply creates a `PENDING` approval |
| `AWAITING_APPROVAL` | `IN_PROGRESS` | Approval decision (approve or reject) |
| `IN_PROGRESS` | `RESOLVED` | Staff `PATCH /status` |
| `RESOLVED` | `IN_PROGRESS` | Staff `PATCH /status` or the requester rejects the fix |
| `RESOLVED` | `CLOSED` | Requester confirmation only |
| `CLOSED` | `IN_PROGRESS` | Requester reopens a closed ticket |

Entering `RESOLVED` stamps `resolvedAt`; closing stamps `requesterConfirmedAt`; moving back to `IN_PROGRESS` from `RESOLVED`/`CLOSED` clears both. Ticket rows also carry a `version` column, so two concurrent edits produce `409` instead of silently overwriting each other.

### Requester confirmation

The requester has the final say on a `RESOLVED` ticket:

- `POST /api/tickets/{id}/resolution` with `{ "confirmed": true, "comment": "Working now" }` — closes the ticket, stamps `requesterConfirmedAt`, and notifies the assignee. Repeating this on an already-`CLOSED` ticket is a no-op: the status stays `CLOSED` and no second notification is sent.
- `POST /api/tickets/{id}/resolution` with `{ "confirmed": false, "comment": "Still broken" }` — moves the ticket back to `IN_PROGRESS`, clears `resolvedAt` and `requesterConfirmedAt`, stores the note in `requesterFeedback`, and notifies the assignee (or managers if nobody is assigned). This is also how the requester reopens a `CLOSED` ticket.

Only the employee who raised the ticket may call this endpoint; staff cannot close or reopen a `CLOSED` ticket. `comment` is optional and limited to 2000 characters. Calling this before the ticket is `RESOLVED` or `CLOSED` returns `409`.

---

## Approvals  `/api/approvals`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/approvals` | MANAGER+ | List approvals. Query: `?status=PENDING` |
| `POST` | `/api/approvals/{id}/decision` | MANAGER+ | Approve or reject. Body: `{approve: true, comment: "..."}`. `approve` is required; a missing flag returns `400`. |

An approval is single-use and independent:

- The approver cannot be the person who wrote the response (`403`).
- The ticket must still be `AWAITING_APPROVAL` and the approval still `PENDING`; otherwise the request returns `409`. This makes a second or concurrent decision safe — exactly one decision wins.
- Approving sends the held reply (`AWAITING_APPROVAL → IN_PROGRESS`). Rejecting marks the response `REJECTED`, returns the ticket to `IN_PROGRESS`, and notifies the author. Rejected replies are immutable; write a new reply instead.

A unique index on `approvals(response_id)` means one response can never have two competing approval rows.

---

## Notifications  `/api/notifications`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/notifications` | Any | List the caller's notifications |
| `POST` | `/api/notifications/{id}/read` | Any | Mark a notification as read |

---

## Analytics  `/api/analytics`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/analytics/summary` | MANAGER+ | Aggregate stats. Query: `?days=30&category=IT`. The window is clamped to 1–365 days. |
| `GET`  | `/api/analytics/insights` | MANAGER+ | AI-generated narrative insights |

---

## Forecast  `/api/forecast`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/forecast` | MANAGER+ | Predicted ticket volume. Query: `?horizonDays=7` |

---

## Reports  `/api/reports`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/reports/weekly` | MANAGER+ | Weekly report JSON. Query: `?department=IT`. Both the 7-day summary and the 28-day forecast use only that department's tickets. |
| `GET`  | `/api/reports/weekly/pdf` | MANAGER+ | Download weekly report as PDF |

---

## Compliance  `/api/compliance`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/compliance/overview` | MANAGER+ | Risk/approval headline metrics. Query: `?days=30`. The window is clamped to 1–365 days. |
| `GET`  | `/api/compliance/report` | MANAGER+ | Risk summary with recent flagged items. Query: `?days=30`. The window is clamped to 1–365 days. |
| `GET`  | `/api/compliance/audit` | MANAGER+ | Paginated audit log. Query: `?page=0&size=50` |

Risk counts, flagged replies and approval decisions (`approved` / `rejected`) cover the requested window. `pendingApprovals` is the current backlog and is not period-filtered. Risk screening is rule-based and null-safe. Long or grouped card/ID numbers (`13–16` digits, or four groups of four digits) are `HIGH` risk; absolute promises, legal wording and blaming language are `MEDIUM`; gendered wording and contact details are `LOW`. Text is NFKC-normalised and stripped of control/zero-width characters before matching, and every stored reply is cleaned the same way. Audit details are collapsed to a single line and capped at 4,000 characters.

When `AI_PROVIDER=llm`, configuration is validated at startup: the provider must be `mock` or `llm`, the base URL must be an absolute `http(s)` URL, the model must be non-blank, the timeout must be 1–120 seconds, and the API key may not contain control characters. Ticket text is wrapped in explicit untrusted-data delimiters before it reaches the model, so prompt-injection attempts cannot close the block. Model replies are cleaned and length-limited; blank, oversized, non-finite-confidence or number-inventing output falls back to the deterministic keyword classifier/template/narrator.

---

## Users  `/api/users`  *(staff list MANAGER+, all other operations ADMIN)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/users/staff` | MANAGER+ | Active AGENT and MANAGER accounts (for assignment) |
| `GET`  | `/api/users` | ADMIN | All user accounts, including deactivated ones |
| `POST` | `/api/users` | ADMIN | Create an account. Body: `{fullName, email, password, role, department}` |
| `PATCH`| `/api/users/{id}/deactivate` | ADMIN | Soft-deactivate a user account (blocks sign-in) |
| `PATCH`| `/api/users/{id}/activate` | ADMIN | Reactivate a deactivated account |
| `DELETE`| `/api/users/{id}` | ADMIN | Permanently delete an account that has no ticket history |

User objects include `active`. Only `AGENT`s may have a `department`; `EMPLOYEE`, `MANAGER` and `ADMIN` accounts must leave it `null`. An ADMIN cannot deactivate their own account or the last active admin.

`DELETE` returns `204 No Content`. It is deliberately refused with `400` for anyone who has raised a ticket, been assigned a ticket, written a reply or decided an approval — use `deactivate` for those accounts so the audit trail stays intact. Your own account and the last active admin cannot be deleted; the last-admin check counts only active admins, matching deactivation.

---

## Error Responses

All errors return a consistent JSON envelope:

```json
{
  "timestamp": "2026-11-06T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "email": "must not be blank" }
}
```

| Status | When |
|--------|------|
| 400 | Validation failure or bad request body |
| 401 | Missing or invalid/expired JWT |
| 403 | Authenticated but not allowed to perform this action or see this resource |
| 404 | Resource not found |
| 409 | Conflict: duplicate email, an illegal ticket status transition, a blocked action, or two people editing the same record at once |
| 500 | Unexpected server error (see backend logs) |
