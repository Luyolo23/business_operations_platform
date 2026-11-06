# Architecture — AI-Powered Business Operations Assistant

## Overview

The platform is a **modular monolith** built with Java 21 + Spring Boot 3.3.  
Each business capability lives in its own package under `com.capaciti.ops.*` with clearly defined module boundaries.  
The React/TypeScript frontend talks exclusively to the Spring Boot REST API.

---

## Components

```
┌────────────────────────────────────────────────────────────────────────┐
│                         Browser / Mobile                               │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ HTTPS (port 80 in Docker, 5173 in dev)
┌───────────────────────────────▼────────────────────────────────────────┐
│                    Nginx (frontend container)                          │
│  Serves React SPA · proxies /api/* to the backend                     │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ HTTP (port 8080)
┌───────────────────────────────▼────────────────────────────────────────┐
│                  Spring Boot REST API  (backend container)             │
│                                                                        │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │   ticket    │  │   response   │  │   analytics  │  │  forecast  │ │
│  └─────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  compliance │  │    report    │  │  workflow /  │  │    ai.*    │ │
│  │             │  │   (PDF)      │  │  notification│  │ (LLM/mock) │ │
│  └─────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────────────┐ │
│  │  security   │  │   user       │  │  common (PageResponse,        │ │
│  │  (JWT, BCr.)│  │   (CRUD)     │  │   GlobalExceptionHandler)     │ │
│  └─────────────┘  └──────────────┘  └──────────────────────────────┘ │
└───────────────────────────────┬────────────────────────────────────────┘
                                │ JDBC (port 5432)
┌───────────────────────────────▼────────────────────────────────────────┐
│                     PostgreSQL 16 (postgres container)                 │
│  Schema managed by Flyway  (V1, V2, V3 migrations)                    │
└────────────────────────────────────────────────────────────────────────┘
                                │ HTTPS
                         (optional)
┌───────────────────────────────▼────────────────────────────────────────┐
│               External LLM API  (e.g. Groq / OpenAI)                  │
│  Used only when AI_PROVIDER=llm. Falls back to keyword classifier.    │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Module Map

| Package | Responsibility | Key classes |
|---------|----------------|-------------|
| `ticket` | Create, classify, route & manage tickets | `TicketService`, `TicketController`, `Ticket` |
| `ai` | Classify tickets & generate replies (keyword or LLM) | `KeywordTicketClassifier`, `LlmTicketClassifier`, `LlmResponseGenerator` |
| `response` | Draft & approve ticket replies | `ResponseService`, `ApprovalService` |
| `analytics` | Aggregate metrics & narrative insights | `AnalyticsService`, `InsightService` |
| `forecast` | Linear trend prediction | `ForecastService`, `LinearTrend` |
| `report` | Weekly PDF report | `ReportService`, `PdfReportGenerator` |
| `compliance` | Risk scoring, audit log | `ComplianceService`, `BiasChecker`, `AuditService` |
| `workflow` | Routing rules (DB-driven) | `WorkflowService`, `RoutingRule` |
| `notification` | In-app notifications & email stub | `NotificationService`, `LoggingEmailSender` |
| `user` | User management (ADMIN CRUD) | `UserController`, `UserRepository` |
| `security` | JWT auth, BCrypt password encoding | `JwtService`, `JwtAuthFilter`, `SecurityConfig` |
| `config` | Data seeder (dev), Swagger metadata | `DataSeeder`, `MetaController` |
| `common` | Shared DTOs, global error handling | `GlobalExceptionHandler`, `ApiError`, `PageResponse` |

---

## Data Flow – Ticket Lifecycle

```
Employee POSTs /api/tickets
        │
        ▼
KeywordTicketClassifier (or LlmTicketClassifier)
        │  category + priority + confidence
        ▼
WorkflowService  →  assigns the ticket to the right AGENT
        │
        ▼
NotificationService  →  in-app notification to the assignee
        │
        ▼
AGENT POSTs /api/tickets/{id}/responses (tone=FORMAL|FRIENDLY|URGENT)
        │
        ▼
[TemplateResponseGenerator | LlmResponseGenerator]
        │  draft body
        ▼
BiasChecker / ComplianceService  →  risk level (NONE | LOW | MEDIUM | HIGH)
        │
        ├── risk NONE/LOW   →  AUTO APPROVED  →  ticket → IN_PROGRESS
        │
        └── risk MEDIUM/HIGH →  ApprovalService creates PENDING approval
                                MANAGER approves/rejects via /api/approvals/{id}/decision
```

---

## Security Model

- All endpoints (except `/api/auth/*`, `/actuator/health`, Swagger) require a JWT **Bearer** token.
- Tokens are signed with HMAC-SHA256 using `JWT_SECRET`, expire in 8 hours.
- Roles: `EMPLOYEE < AGENT < MANAGER < ADMIN` (enforced in `SecurityConfig`).
- Passwords are BCrypt-hashed (strength 10) — never stored in plain text.

---

## Database Schema (summary)

In `db/migration/V1__init_schema.sql`. Key tables:

| Table | Purpose |
|-------|---------|
| `users` | All accounts (employees, agents, managers, admins) |
| `routing_rules` | Department → SLA mapping (seeded in V2) |
| `tickets` | Main work items |
| `response_templates` | Per-category, per-tone message stubs (seeded in V2) |
| `ticket_responses` | Agent-drafted replies |
| `approvals` | Manager approval records |
| `risk_assessments` | Compliance risk records per response |
| `audit_logs` | Immutable event log |
| `notifications` | In-app notification inbox |
