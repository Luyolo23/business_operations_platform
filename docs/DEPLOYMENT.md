# Deployment Guide

The production artifact is the three-container stack in `docker-compose.full.yml`:
PostgreSQL 16, the Spring Boot API and an Nginx container serving the built React app
and proxying `/api/*` to the backend.

Everything below was verified against `main` at commit `67932ba`:

```bash
docker compose -f docker-compose.full.yml up -d --build
# backend health: healthy
# GET http://localhost:8081/api/config -> {"demoMode":false}
# frontend: HTTP 200 with all security headers
```

---

## 1. Prerequisites

- Docker Engine 24+ with the Compose v2 plugin
- A host with at least 2 GB RAM (Postgres + JVM + Nginx)
- A domain name and TLS termination (see section 7)
- A strong `JWT_SECRET` (32 bytes minimum — the app refuses to start otherwise)

---

## 2. Environment variables

Copy the template and fill it in on the server. Never commit the real `.env`.

```bash
cp .env.example .env
openssl rand -base64 48   # paste into JWT_SECRET
chmod 600 .env
```

| Variable | Required | Purpose |
|----------|----------|---------|
| `JWT_SECRET` | **yes** | Signs auth tokens. 32+ bytes. Rotating it invalidates all sessions. |
| `DB_PASSWORD` | **yes** | Postgres password used by both the database and the backend. |
| `DB_URL` / `DB_USER` | no | Defaults are correct inside Compose. Override only for an external database. |
| `AI_PROVIDER` | no | `mock` (default, deterministic templates) or `llm`. |
| `AI_API_KEY` | only for `llm` | Provider key. Leave empty to run fully offline. |
| `AI_BASE_URL` / `AI_MODEL` | no | Defaults to Groq's OpenAI-compatible endpoint. |
| `CORS_ORIGINS` | no | Only needed if the browser calls the API directly from a different origin. The bundled Nginx proxy is same-origin. |
| `SPRING_PROFILES_ACTIVE` | no | **Leave empty in production.** `dev` enables Swagger, demo tickets and demo-login pills. |
| `APP_SEED_ENABLED` | no | First boot only — see section 4. |
| `APP_SEED_DEMO_TICKETS` | no | Keep `false` in production. |

---

## 3. Start the stack

```bash
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```

Expected after ~30 s:

- `ops-full-postgres-1` — healthy
- `ops-full-backend-1` — healthy
- `ops-full-frontend-1` — healthy (HTTP check on port 8080 inside the container)

Verify:

```bash
docker compose -f docker-compose.full.yml logs --tail=50 backend
curl -fsS http://localhost:8080/actuator/health      # {"status":"UP"}
curl -fsS http://localhost:8081/api/config           # {"demoMode":false}
curl -fsS http://localhost:8081/ -o /dev/null -w '%{http_code}\n'
```

Flyway applies `V1`–`V7` on first boot. Watch for `Successfully applied` in the backend
log; a checksum error means someone edited an applied migration (see section 9).

---

## 4. First-run admin bootstrap (important)

`main` ships with the data seeder **off**, and self-registration only creates
`EMPLOYEE` accounts. A brand-new database therefore has no admin, and only an admin can
create staff accounts. Bootstrap it once:

```bash
# on the server, in the project directory
APP_SEED_ENABLED=true APP_SEED_DEMO_TICKETS=false \
  docker compose -f docker-compose.full.yml up -d --force-recreate backend

# confirm the log line
docker compose -f docker-compose.full.yml logs backend | grep 'Seeded demo users'
```

Log in at the public URL as `admin@ops.local` / `Passw0rd!`, then:

1. **People → New account** — create your real ADMIN account with a strong password.
2. Create real MANAGER and AGENT accounts (agents need a department).
3. Log in as the new admin and **deactivate** the demo accounts (`admin@`, `manager@`,
   `agent.*@`, `employee@`). The last-active-admin guard means you must create the real
   admin *before* deactivating the demo one.
4. Turn the seeder back off:

```bash
APP_SEED_ENABLED=false docker compose -f docker-compose.full.yml up -d --force-recreate backend
```

The seeder is idempotent: it returns immediately if any user already exists.

> There is currently no change-password endpoint. Treat the demo password as public
> and make sure every seeded account is deactivated.

---

## 5. Data and backups

The database lives in the `pgdata` named volume.

```bash
# nightly backup
docker compose -f docker-compose.full.yml exec -T postgres \
  pg_dump -U ops -d opsdb | gzip > "opsdb-$(date +%F).sql.gz"

# restore into an empty database
gunzip -c opsdb-2026-11-20.sql.gz | \
  docker compose -f docker-compose.full.yml exec -T postgres psql -U ops -d opsdb
```

Copy backups off the host. The Postgres port is published only on `127.0.0.1:5434`
for pgAdmin; remove that mapping on a public server.

---

## 6. Updating and rolling back

```bash
git pull --ff-only
docker compose -f docker-compose.full.yml up -d --build
docker compose -f docker-compose.full.yml ps
```

- New `V<n>__*.sql` migrations are applied automatically. Never edit an applied one.
- Take a backup before every deploy that includes a migration.
- Flyway is forward-only. A code rollback does **not** undo a migration; restore the
  backup if a migration must be reverted.

Prefer immutable image tags in a real environment:

```bash
docker build -t ops-backend:2026-11-20 ./backend
docker build -t ops-frontend:2026-11-20 ./frontend
```

---

## 7. TLS and the public edge

The Compose file publishes:

- `8081` — frontend (Nginx)
- `8080` — backend, bound to `127.0.0.1` only

Put Caddy, Traefik or Nginx in front of the host and proxy HTTPS traffic to
`127.0.0.1:8081`. A minimal Caddyfile:

```caddyfile
ops.example.com {
    reverse_proxy 127.0.0.1:8081
}
```

Do not expose `8081` or `8080` directly to the internet. The frontend already sends
`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`,
`Permissions-Policy` and a restrictive `Content-Security-Policy`; adding HSTS at the
edge is recommended.

If the API is served from a different origin than the frontend, set
`CORS_ORIGINS=https://api.example.com` and expose the backend through the proxy.
Otherwise keep the bundled same-origin Nginx proxy.

---

## 8. Production checklist

- [ ] `JWT_SECRET` is random, 32+ bytes, stored only in `.env` / secret manager
- [ ] `DB_PASSWORD` changed from the default
- [ ] `SPRING_PROFILES_ACTIVE` empty (no Swagger, no demo pills, no demo tickets)
- [ ] `APP_SEED_ENABLED=false` after bootstrap
- [ ] Demo accounts deactivated; real admin created
- [ ] `docker-compose.full.yml ps` shows all containers healthy
- [ ] `GET /api/config` returns `{"demoMode":false}`
- [ ] `GET /actuator/health` returns `{"status":"UP"}`
- [ ] HTTPS terminates at the edge; only 443 is public
- [ ] Nightly `pg_dump` backup copied off-host
- [ ] Logs monitored (`docker compose logs -f backend`)
- [ ] Database port `5434` not reachable from the internet

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Backend exits immediately, log says `JWT_SECRET` missing or too short | Set a 32+ byte secret in `.env`; the default profile has no fallback. |
| `Validate failed: migration checksum mismatch` | Someone edited an applied `V*.sql`. Revert the file; add a new `V<n>` migration instead. |
| `Flyway ... detected failed migration` | The failed migration is partially applied. Restore the pre-deploy backup (`flyway repair` alone is not enough for DDL). |
| Frontend container unhealthy | Check the Nginx log: `docker compose -f docker-compose.full.yml logs frontend`. The healthcheck uses `wget http://127.0.0.1:8080/`. |
| `/api/config` returns 502 | The backend is not healthy yet. `docker compose ... logs backend`; Nginx retries DNS per request. |
| Login works but the dashboard is empty | Expected on a fresh database — no tickets yet. Create one through the UI. |
| Actions jobs fail in 2–4 s with no steps | GitHub account billing lock, not a code problem. See `docs/team/MAINTAINER_MERGE.md` and the note below. |

---

## 10. CI/CD status

There is **no automated deployment job** — deploys are the manual steps above. CI
(`.github/workflows/ci.yml`) builds and tests every push and PR.

If every CI job fails within seconds, click a failed job and read the annotation. A
GitHub-hosted runner failure with **"The job was not started because your account is
locked due to a billing issue"** is an account problem: fix it in **GitHub → Settings →
Billing & plans** (payment method / spending limit), then re-run the workflow from the
**Actions** tab (the workflow now supports `workflow_dispatch`).

Until that is resolved, run the same checks locally:

```bash
make test                                      # backend unit tests
make it                                        # Testcontainers integration tests
cd frontend && npm ci && npm run build         # typecheck + production bundle
docker build -t ops-backend ./backend
docker build -t ops-frontend ./frontend
```
