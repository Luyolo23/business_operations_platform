# AI-Powered Business Operations Assistant

Reference implementation for the CAPACITI AI Bootcamp 8-week project.
**Stack:** Java 21 + Spring Boot 3.3 (modular monolith) · PostgreSQL 16 · React + TypeScript (Vite).

Employees raise internal requests ("tickets"). The platform classifies them, routes them to the right
department, drafts replies, tracks analytics, forecasts workload, checks responses for risk/bias, and
automates approvals and notifications.

## Quick start (backend)

Requirements: JDK 21, Maven 3.9+ (or IntelliJ's bundled Maven), Docker (for PostgreSQL).

```bash
docker compose up -d                  # PostgreSQL on localhost:5432 (db opsdb, user/pass ops/ops)
cd backend
mvn spring-boot:run                   # Flyway creates the schema, the seeder adds demo data
```

* Swagger UI (try every endpoint): http://localhost:8080/swagger-ui.html
* Health: http://localhost:8080/actuator/health
* Demo logins (password `Passw0rd!`): `admin@ops.local`, `manager@ops.local`, `agent.it@ops.local`,
  `agent.hr@ops.local`, `agent.finance@ops.local`, `agent.ops@ops.local`, `employee@ops.local`

Run the unit tests: `cd backend && mvn test`

## Quick start (frontend)
```bash
cd frontend && npm install && npm run dev     # http://localhost:5173
```
Log in as `manager@ops.local` / `Passw0rd!` to see every screen. Details in `frontend/README.md`.

### Try it with curl
```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"employee@ops.local","password":"Passw0rd!"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s localhost:8080/api/tickets -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"VPN not working","description":"I cannot connect to the VPN from home"}'
```

## Weeks -> modules

| Week | Feature | Package (`com.capaciti.ops.…`) | Main endpoints |
|------|---------|-------------------------------|----------------|
| 1 | Ticket classification engine | `ticket`, `ai.KeywordTicketClassifier` | `POST /api/tickets`, `GET /api/tickets` |
| 2 | AI response generator (templates + tone) | `response`, `ai.*ResponseGenerator` | `POST /api/tickets/{id}/responses` |
| 3 | Analytics & tracking dashboard | `analytics` | `GET /api/analytics/summary`, `/insights` |
| 4 | Weekly business report (PDF) | `report` | `GET /api/reports/weekly`, `/weekly/pdf` |
| 5 | Predictive insights | `forecast` | `GET /api/forecast` |
| 6 | Compliance & risk layer | `compliance` | `GET /api/compliance/report`, `/audit` |
| 7 | Workflow automation | `workflow`, `notification`, `response.Approval*` | `GET/POST /api/approvals`, `/api/notifications` |
| 8 | Deployment & documentation | `docs/`, `docker-compose.yml` | – |

## Repository layout
```
backend/      Spring Boot app (Maven)
frontend/     React + TypeScript app (Vite)
  src/main/resources/db/migration/   Flyway SQL (owned by the DB teammate)
  src/main/java/com/capaciti/ops/    one package per feature module
docs/         ARCHITECTURE.md · API.md · TEAM_GUIDE.md
docker-compose.yml
```

See `docs/TEAM_GUIDE.md` for who owns what and the contracts between the four of you.
