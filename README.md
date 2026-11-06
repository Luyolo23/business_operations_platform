# AI-Powered Business Operations Assistant

> Reference implementation for the CAPACITI AI Bootcamp 8-week project.  
> **Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · React + TypeScript (Vite) · Docker

Employees raise internal requests ("tickets"). The platform automatically classifies them, routes them to the right department, drafts AI-generated replies, tracks SLA compliance, forecasts workload, checks responses for risk and bias, and automates the approval workflow.

---

## Table of Contents

1. [Quick Start – Backend (dev)](#quick-start--backend-dev)
2. [Quick Start – Frontend (dev)](#quick-start--frontend-dev)
3. [Full-Stack Docker Compose](#full-stack-docker-compose)
4. [Demo Accounts](#demo-accounts)
5. [Try it with curl](#try-it-with-curl)
6. [How to Store API Keys Safely](#how-to-store-api-keys-safely)
7. [Feature Modules](#feature-modules)
8. [Repository Layout](#repository-layout)
9. [Running Tests](#running-tests)
10. [Documentation](#documentation)

---

## Quick Start – Backend (dev)

**Requirements:** JDK 21, Maven 3.9+, Docker (for PostgreSQL).

```bash
# 1. Start the dev database on localhost:5433
docker compose up -d

# 2. Run the backend with demo data enabled
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Or use the Makefile shortcut:

```bash
make backend       # starts Postgres then runs Spring Boot with demo seed
```

Once started:

| URL | Purpose |
|-----|---------|
| http://localhost:8080/swagger-ui.html | Interactive API explorer |
| http://localhost:8080/actuator/health | Health check |

> **Note:** The dev Postgres instance listens on **`localhost:5433`** (mapped from container port 5432).  
> The backend's `application.yml` is pre-configured for this port.

---

## Quick Start – Frontend (dev)

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173
```

Or:

```bash
make frontend
```

The Vite dev server proxies `/api/*` requests to `http://localhost:8080`, so CORS is never an issue during development.

---

## Full-Stack Docker Compose

Runs Postgres + Spring Boot backend + Nginx-served React frontend in containers:

```bash
# 1. Copy the env template and fill in your values
cp .env.example .env

# 2. Build and start everything
docker compose -f docker-compose.full.yml up --build

# Or with the Makefile:
make full
```

| URL | What's there |
|-----|-------------|
| http://localhost:8081 | React frontend (via Nginx) |
| http://localhost:8080 | Backend API / Swagger UI |

---

## Demo Accounts

> Password for all demo accounts: **`Passw0rd!`**  
> Demo data is seeded only when `SPRING_PROFILES_ACTIVE=dev` is active.

| Email | Role | Notes |
|-------|------|-------|
| `admin@ops.local` | ADMIN | Full access including user management |
| `manager@ops.local` | MANAGER | Dashboard, approvals, reports, forecast, compliance |
| `agent.it@ops.local` | AGENT (IT) | Handles IT tickets |
| `agent.hr@ops.local` | AGENT (HR) | Handles HR tickets |
| `agent.finance@ops.local` | AGENT (Finance) | Handles Finance tickets |
| `agent.ops@ops.local` | AGENT (Operations) | Handles Operations tickets |
| `employee@ops.local` | EMPLOYEE | Raises and tracks their own tickets |

---

## Try it with curl

**Login and create a ticket in two commands:**

```bash
# Get a token
TOKEN=$(curl -s localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"employee@ops.local","password":"Passw0rd!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Create a ticket (auto-classified and routed)
curl -s localhost:8080/api/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"VPN not working","description":"I cannot connect to the VPN from home"}' \
  | python3 -m json.tool
```

---

## How to Store API Keys Safely

The AI features (ticket classification + reply generation) can use any OpenAI-compatible API such as [Groq](https://console.groq.com). Here is how to manage the key securely:

### Local development

```bash
# Never hard-code keys in source files.
# Store them in a .env file (already in .gitignore):
cp .env.example .env
# Then edit .env and set AI_API_KEY=gsk_...
```

The backend reads `${AI_API_KEY}` from the environment so the key never touches the code.

### Docker Compose

```bash
docker compose -f docker-compose.full.yml --env-file .env up
```

The compose file injects `AI_API_KEY` from your `.env` into the container at runtime.

### GitHub Actions / CI

1. Go to **Settings → Secrets and variables → Actions** in your repo.
2. Add `AI_API_KEY` as a repository secret.
3. In your workflow file:

```yaml
env:
  AI_API_KEY: ${{ secrets.AI_API_KEY }}
```

### Without an API key

Set `AI_PROVIDER=mock` (the default) to use the built-in keyword classifier and response templates — no external API call, fully deterministic, perfect for testing.

---

## Feature Modules

| Week | Feature | Package | Key endpoints |
|------|---------|---------|---------------|
| 1 | Ticket classification & routing | `ticket`, `ai.KeywordTicketClassifier` | `POST /api/tickets`, `GET /api/tickets` |
| 2 | AI response generator | `response`, `ai.*ResponseGenerator` | `POST /api/tickets/{id}/responses` |
| 3 | Analytics & tracking | `analytics` | `GET /api/analytics/summary`, `/insights` |
| 4 | Weekly PDF report | `report` | `GET /api/reports/weekly`, `/weekly/pdf` |
| 5 | Predictive workload forecast | `forecast` | `GET /api/forecast` |
| 6 | Compliance & risk layer | `compliance` | `GET /api/compliance/report`, `/audit` |
| 7 | Workflow automation & notifications | `workflow`, `notification`, `response.Approval*` | `GET/POST /api/approvals`, `/api/notifications` |
| 8 | Deployment & documentation | `docs/`, `docker-compose.*.yml`, `Makefile` | — |

---

## Repository Layout

```
.
├── backend/                   Spring Boot app (Maven)
│   ├── src/main/java/com/capaciti/ops/
│   │   ├── ai/                Classifier & response generators (keyword + LLM)
│   │   ├── analytics/         Metrics & insight narrative
│   │   ├── common/            Shared DTOs, GlobalExceptionHandler
│   │   ├── compliance/        Risk scoring, audit log
│   │   ├── config/            DataSeeder (dev), Swagger metadata
│   │   ├── forecast/          Linear trend predictor
│   │   ├── notification/      In-app notifications, email stub
│   │   ├── report/            Weekly PDF report
│   │   ├── response/          Reply drafting & approval workflow
│   │   ├── security/          JWT, BCrypt, SecurityConfig
│   │   ├── ticket/            Core ticket entity & service
│   │   ├── user/              User management (ADMIN CRUD)
│   │   └── workflow/          DB-driven routing rules
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml   ← demo seeder config (dev profile)
│   │   └── db/migration/         ← Flyway SQL (V1, V2, V3)
│   └── src/test/
│       └── java/com/capaciti/ops/
│           └── TicketFlowIntegrationTest.java  (+ 4 unit test classes)
│
├── frontend/                  React + TypeScript (Vite)
│   ├── src/
│   │   ├── api/               API client + TypeScript types
│   │   ├── auth/              AuthContext (JWT storage)
│   │   ├── components/        Layout, UI primitives, workflow animation
│   │   └── pages/             One file per screen
│   ├── Dockerfile             Multi-stage Node → Nginx build
│   └── nginx.conf             SPA routing + /api proxy
│
├── backend/Dockerfile         Multi-stage Maven → JRE build
├── docker-compose.yml         Dev Postgres only (port 5433)
├── docker-compose.full.yml    Full stack: Postgres + backend + frontend
├── .env.example               Environment variable template
├── Makefile                   Developer shortcuts (make backend, make full, …)
└── docs/
    ├── ARCHITECTURE.md        Service diagram & module map
    ├── API.md                 Full endpoint reference
    └── TEAM_GUIDE.md          Ownership, branching, secret management
```

---

## Running Tests

```bash
# Unit tests only (no Docker required)
cd backend && mvn test -DexcludedGroups=integration

# Integration tests (Docker required for Testcontainers)
cd backend && mvn test -Dgroups=integration

# Live Groq tests (uses AI_API_KEY from .env; excluded from normal runs)
make live

# Makefile shortcuts
make test     # unit tests
make it       # integration tests
make live     # live Groq API tests
```

---

## Documentation

| Document | Contents |
|----------|---------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Service diagram, module map, ticket lifecycle, security model |
| [`docs/API.md`](docs/API.md) | All REST endpoints with auth requirements and request/response shapes |
| [`docs/TEAM_GUIDE.md`](docs/TEAM_GUIDE.md) | Ownership, branching, how to add modules, secrets guide |
| [`frontend/README.md`](frontend/README.md) | Frontend-specific setup and design notes |
