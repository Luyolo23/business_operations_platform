# Demo Guide — AI-Powered Business Operations Assistant

One-page walkthrough for showing the platform to the team.

> **Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 · React + TypeScript (Vite) · Docker

---

## 1. Start the stack

```bash
make up          # dev Postgres on localhost:5433
make backend     # Spring Boot + demo seed (terminal 1)
make frontend    # Vite dev server   (terminal 2)
make db          # psql shell         (terminal 3, optional)
```

| URL | What's there |
|---|---|
| http://localhost:5173 | React frontend (dev) |
| http://localhost:8080/swagger-ui.html | Interactive API |
| http://localhost:8080/actuator/health | Health check |
| `localhost:5433` | Postgres (db `opsdb`, user `ops`, password `ops`) |

Full Docker instead: `make full` → frontend http://localhost:8081, backend :8080. Shut down with `make full-down`.

---

## 2. Who can sign in

All demo passwords are **`Passw0rd!`**. Roles: `EMPLOYEE < AGENT < MANAGER < ADMIN`.

| Email | Role | Department |
|---|---|---|
| `admin@ops.local` | ADMIN | — |
| `manager@ops.local` | MANAGER | — |
| `agent.it@ops.local` | AGENT | IT |
| `agent.hr@ops.local` | AGENT | HR |
| `agent.finance@ops.local` | AGENT | Finance |
| `agent.ops@ops.local` | AGENT | Operations |
| `employee@ops.local` | EMPLOYEE | — |

- **Self-registration** ("Create an account" on the login page) always creates an **EMPLOYEE**.
- **Staff accounts** (AGENT/MANAGER/ADMIN) are created by an ADMIN on the **People** page (or `POST /api/users`).
- Deactivated users cannot sign in, but their ticket history is kept. An ADMIN can switch them back on.

| Can do | EMPLOYEE | AGENT | MANAGER | ADMIN |
|---|---|---|---|---|
| Create ticket | ✅ | ✅ | ✅ | ✅ |
| See tickets | own | dept + assigned | all | all |
| Change status / reclassify | ❌ | ✅ | ✅ | ✅ |
| Reassign | ❌ | ❌ | ✅ | ✅ |
| Draft AI reply / write a reply | ❌ | ✅ | ✅ | ✅ |
| Resolve or close a ticket (after a reply) | ❌ | ✅ | ✅ | ✅ |
| Approve / reject | ❌ | ❌ | ✅ | ✅ |
| Dashboard · Forecast · Reports · Compliance | ❌ | ❌ | ✅ | ✅ |
| Manage users (People page) | ❌ | ❌ | ❌ | ✅ |

---

## 3. See the data in Postgres

```bash
make db
# or: docker exec -it ops-postgres psql -U ops -d opsdb
# or from host: psql -h localhost -p 5433 -U ops -d opsdb
```

GUI (DBeaver/pgAdmin): host `localhost`, port `5433`, db `opsdb`, user `ops`, password `ops`.

```sql
\dt
SELECT id,email,role,department,active FROM users ORDER BY id;
SELECT id,title,category,priority,status,assignee_id,classification_source,
       classification_confidence,escalated,created_at
FROM tickets ORDER BY id DESC LIMIT 10;
SELECT * FROM ticket_responses ORDER BY id DESC;
SELECT * FROM risk_assessments ORDER BY id DESC;
SELECT * FROM approvals ORDER BY id DESC;
SELECT * FROM notifications ORDER BY id DESC LIMIT 10;
SELECT * FROM audit_logs ORDER BY id DESC LIMIT 10;
SELECT * FROM flyway_schema_history;
```

---

## 4. Where the demo data comes from

| Source | File | Runs when |
|---|---|---|
| Schema + templates + routing rules/SLAs | `backend/src/main/resources/db/migration/V1–V3` | Every start (Flyway) |
| 7 demo users + 80 tickets | `backend/.../config/DataSeeder.java` | Only with `SPRING_PROFILES_ACTIVE=dev` **and** an empty `users` table |

Responses, approvals, notifications and audit logs are **created live** as you use the app — they are not seeded.

**Reset everything:** `docker compose down -v` then `make backend`.

### Switch off the demo data

`make backend` runs with `SPRING_PROFILES_ACTIVE=dev`, which enables the seeder. To start without demo users and tickets:

```bash
# 1. Start the backend without the dev profile
cd backend && mvn spring-boot:run

# or force seeding off while keeping the dev profile
SPRING_PROFILES_ACTIVE=dev APP_SEED_ENABLED=false mvn spring-boot:run
```

The seeder only runs when the `users` table is empty, so turning it off does **not** delete rows that already exist. To remove existing demo rows, reset the volume with `docker compose down -v` (or delete the rows with SQL), then start again with seeding disabled. Schema, templates and routing rules still come from the Flyway migrations and are always applied.

---

## 5. Makefile targets

| Target | Does |
|---|---|
| `make help` (or `make`) | List targets |
| `make up` / `make down` | Start / stop dev Postgres (keeps data) |
| `make db` | Open psql |
| `make backend` | Postgres + Spring Boot with demo seed |
| `make frontend` | Vite dev server |
| `make test` / `make it` | Unit / integration tests (integration needs Docker) |
| `make full` / `make full-down` | Full Docker stack / stop **and wipe** |
| `make live` | Live Groq tests (needs `AI_API_KEY`) |
| `make clean` | Remove build artifacts |

---

## 6. What happens to a ticket

```
EMPLOYEE creates ticket
   → classifier (keyword by default; LLM if AI_PROVIDER=llm, with keyword fallback)
   → category + priority + confidence
   → routing rule picks department/SLA, least-loaded AGENT assigned
   → notifications + audit log
AGENT replies — either let the assistant draft it, or write it manually
   → risk check (PII, discrimination, promises, legal, tone, gendered, emails)
      ├─ NONE/LOW   → auto-approved → sent → IN_PROGRESS + firstResponseAt
      └─ MEDIUM/HIGH → AWAITING_APPROVAL → MANAGER approves/rejects
AGENT / MANAGER / ADMIN may then mark the ticket RESOLVED (or CLOSED)
   → allowed only after the first reply has reached the requester
SLA escalation every 15 min; urgent tickets notify managers immediately.
```

### What triggers manager approval?

Every reply — AI-drafted or manually written — is screened by the same rules. The overall level is the highest rule matched:

| Level | Triggers | Outcome |
|---|---|---|
| `HIGH` | a 13–16 digit number (possible ID/card), discriminatory language | held for approval |
| `MEDIUM` | absolute promises (`guarantee`, `definitely will`, `we promise`, `100%`), legal wording (`legally`, `lawsuit`, `sue`, `liable`, `illegal`), blaming tone | held for approval |
| `LOW` | gendered language, contact details/email addresses | sent, but recorded |
| `NONE` | no rules matched | sent |

`MEDIUM`/`HIGH` creates a pending approval and moves the ticket to `AWAITING_APPROVAL`; managers see it under **Approvals**. Approving sends the reply; rejecting leaves it unsent so the agent can rewrite it.

### The Compliance tab

**Compliance** is the audit view for the assistant's behaviour, not a separate workflow. It shows:

- how replies were classified by risk (`NONE`/`LOW`/`MEDIUM`/`HIGH`),
- how many approvals are pending, approved or rejected,
- the manual routing-override rate (how often staff corrected the AI's team/priority),
- average classification confidence and hours to first reply, by team,
- the specific replies that triggered a rule,
- the raw audit trail (who did what, and when).

---

## 7. Demo script (≈10 min)

1. Log in as `employee@ops.local` → note you only see your own tickets → create *"VPN not working from home"*.
2. In `make db`: show the new row (category, priority, confidence, assignee) and the `audit_logs` entries.
3. Log in as `agent.it@ops.local` → open ticket → either **Draft reply** or write your own in **Write a reply yourself** → a clean reply sends immediately.
4. Write a reply containing *"we guarantee 100%"* or a 16-digit number → it is held for approval; show `risk_assessments` + `approvals`.
5. Log in as `manager@ops.local` → **Approvals** → approve → ticket moves to `IN_PROGRESS`. Then show **Mark resolved** on the ticket (note it is blocked until a reply exists).
6. Show **Overview** (analytics + insights), **Forecast**, **Weekly report → PDF**, and **Compliance** (what the risk/approval/routing metrics mean).
7. Log in as `admin@ops.local` → **People** → create an agent, switch an account off and on, explain self-registration is employee-only.
8. Close with the architecture: browser → Vite/Nginx → Spring Boot modules → Postgres (Flyway-managed).

---

## 8. Gotchas

- `make full` requires `JWT_SECRET` in `.env`.
- `make down` keeps data; use `down -v` to reset.
- Dev DB is `ops/ops` on 5433; full stack uses `DB_PASSWORD` from `.env`.
- User management lives on the **People** page and is ADMIN-only.
- A ticket cannot be resolved or closed until at least one reply has been sent.
- Default `AI_PROVIDER=mock` needs no API key; `llm` uses Groq/OpenAI and falls back to keywords.
- Email is simulated (logged); in-app notifications are real DB rows.
- Never commit `.env`; rotate any exposed `AI_API_KEY`.

More detail: `README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/TEAM_GUIDE.md`.
