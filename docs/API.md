# API Reference

Base URL (local dev): `http://localhost:8080`  
All endpoints except Auth require `Authorization: Bearer <token>`.  
Full interactive docs: **http://localhost:8080/swagger-ui.html**

---

## Authentication  `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | None | Self-register as EMPLOYEE. Body: `{fullName, email, password}` |
| `POST` | `/api/auth/login` | None | Login. Body: `{email, password}`. Returns `{token, expiresInSeconds, user}` |
| `GET`  | `/api/auth/me` | Any | Returns the currently authenticated user |

---

## Tickets  `/api/tickets`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/tickets` | Any | Create a ticket. Body: `{title, description}`. Auto-classifies and routes. |
| `GET`  | `/api/tickets` | Any | List tickets (paginated). Query: `?category=IT&status=OPEN&page=0&size=20` |
| `GET`  | `/api/tickets/{id}` | Any | Get a single ticket (employees see only their own) |
| `PATCH`| `/api/tickets/{id}/status` | AGENT+ | Update status. Body: `{status}`. `RESOLVED`/`CLOSED` are rejected until a reply has been sent. |
| `PATCH`| `/api/tickets/{id}/classification` | AGENT+ | Re-classify. Body: `{category, priority}` |
| `PATCH`| `/api/tickets/{id}/assignee` | MANAGER+ | Reassign. Body: `{userId}` |
| `GET`  | `/api/tickets/{id}/responses` | Any | List all drafted responses for a ticket |
| `POST` | `/api/tickets/{id}/responses` | AGENT+ | Draft a reply. Body: `{tone, body?}`. Omit `body` for an AI/template draft; send `body` for a manually written reply. Both are risk-screened. |

### Replies and resolution

`POST /api/tickets/{id}/responses` accepts a `tone` (`FORMAL`, `FRIENDLY`, `URGENT`) and an optional `body`:

- **No `body`** — the configured generator drafts the text from the category/tone template.
- **With `body`** — the reply is stored exactly as written (`generatedBy: "manual"`). It is still passed through the same risk checker.

In both cases the risk level decides what happens next:

| Risk | Result |
|---|---|
| `NONE` / `LOW` | Auto-approved, sent to the requester, ticket moves to `IN_PROGRESS` and `firstResponseAt` is stamped. |
| `MEDIUM` / `HIGH` | Held as `PENDING_APPROVAL`, an `Approval` is created, ticket moves to `AWAITING_APPROVAL`, and managers are notified. |

A ticket may only move to `RESOLVED` or `CLOSED` after `firstResponseAt` is set (i.e. at least one reply has reached the requester). Otherwise the API returns `400`. Any AGENT, MANAGER or ADMIN who can see the ticket may resolve it; employees cannot change status.

---

## Approvals  `/api/approvals`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/approvals` | MANAGER+ | List approvals. Query: `?status=PENDING` |
| `POST` | `/api/approvals/{id}/decision` | MANAGER+ | Approve or reject. Body: `{approve: true, comment: "..."}` |

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
| `GET`  | `/api/analytics/summary` | MANAGER+ | Aggregate stats. Query: `?days=30&category=IT` |
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
| `GET`  | `/api/reports/weekly` | MANAGER+ | Weekly report JSON. Query: `?department=IT` |
| `GET`  | `/api/reports/weekly/pdf` | MANAGER+ | Download weekly report as PDF |

---

## Compliance  `/api/compliance`  *(MANAGER+)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/compliance/report` | MANAGER+ | Risk summary. Query: `?days=30` |
| `GET`  | `/api/compliance/audit` | MANAGER+ | Paginated audit log. Query: `?page=0&size=20` |

---

## Users  `/api/users`  *(staff list MANAGER+, all other operations ADMIN)*

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET`  | `/api/users/staff` | MANAGER+ | Active AGENT and MANAGER accounts (for assignment) |
| `GET`  | `/api/users` | ADMIN | All user accounts, including deactivated ones |
| `POST` | `/api/users` | ADMIN | Create an account. Body: `{fullName, email, password, role, department}` |
| `PATCH`| `/api/users/{id}/deactivate` | ADMIN | Soft-deactivate a user account (blocks sign-in) |
| `PATCH`| `/api/users/{id}/activate` | ADMIN | Reactivate a deactivated account |

User objects include `active`. Only `AGENT`s may have a `department`; `EMPLOYEE`, `MANAGER` and `ADMIN` accounts must leave it `null`. An ADMIN cannot deactivate their own account or the last active admin.

---

## Error Responses

All errors return a consistent JSON envelope:

```json
{
  "timestamp": "2026-11-06T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": { "email": "must not be blank" }
}
```

| Status | When |
|--------|------|
| 400 | Validation failure or bad request body |
| 401 | Missing or invalid/expired JWT |
| 403 | Authenticated but insufficient role |
| 404 | Resource not found |
| 409 | Conflict (e.g. duplicate email) |
| 500 | Unexpected server error (see backend logs) |
