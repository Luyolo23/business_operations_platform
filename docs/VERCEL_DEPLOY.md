# Deploying on Vercel — how it works and how to do it

## The short answer

Vercel can host **only the React frontend**. It runs builds and serves static files; it does
not run long-lived processes such as a JVM or PostgreSQL. So the project is split in two:

```
             Browser
                │
                ▼
   ┌─────────────────────────┐        ┌──────────────────────────────┐
   │  Vercel                 │  /api  │  Render (or Railway / Fly)   │
   │  React build (dist/)    │ ─────► │  Spring Boot Docker image    │
   │  + /api proxy rewrite   │        │  + managed PostgreSQL        │
   └─────────────────────────┘        └──────────────────────────────┘
```

The "multiple commands" you run locally map like this:

| Local command / container | In the cloud |
|---|---|
| `docker compose up -d` (PostgreSQL) | A managed Postgres database (Render / Neon) |
| `mvn spring-boot:run` (Spring Boot) | A Render **Docker** web service |
| `nginx` serving `frontend/dist` | Vercel static hosting |
| `docker compose up --build` after a change | `git push` → both platforms rebuild |
| `frontend/nginx.conf` proxy to `backend:8080` | `frontend/vercel.json` rewrite `/api/*` → backend URL |

You never run Maven, Java or Nginx by hand in production. Vercel runs `npm run build` inside
`frontend/` and serves `dist/`; Render builds `backend/Dockerfile` and runs the JAR.

## What is already prepared in this repo

- `frontend/vercel.json` — Vite build, SPA fallback (so `/tickets` deep links work), the
  `/api/*` proxy rewrite, and the same security headers Nginx sends.
- `backend/src/main/resources/application.yml` — listens on `$PORT` (Render/Railway/Fly set it).
- `backend/src/main/java/.../SecurityConfig.java` — CORS accepts patterns such as
  `https://*.vercel.app`, so preview deployments work.
- `backend/Dockerfile` — Render builds it directly; no extra Dockerfile needed.
- Flyway runs `V1`–`V7` automatically on the first boot.

Two files you must edit before deploying:

1. `frontend/vercel.json` → replace `https://REPLACE-WITH-YOUR-BACKEND.onrender.com`.
2. Environment variables on Render (below).

---

## Part 1 — Database + backend on Render

You need the backend URL before Vercel can proxy to it, so do this first. Render's free tier
is enough to try it; read [Costs and limits](#costs-and-limits) before using it for real.

### 1.1 Create the Postgres database (~2 min)

1. Sign in at <https://render.com> → **New → Postgres**.
2. Name: `ops-db`. Region: pick the one closest to you (e.g. Frankfurt). **Remember it** —
   the web service must use the same region to reach the database over the internal network.
3. Plan: **Free** (or the cheapest paid plan).
4. **Create Database**, wait until status is *Available*.
5. Open the database → **Connect** → copy the **Internal Database URL**. It looks like:

   ```
   postgresql://ops:AbC123xyz@dpg-abc123-a/opsdb
   ```

6. Split it into the three values the backend needs:

   | Env var | Value | Taken from |
   |---|---|---|
   | `DB_URL` | `jdbc:postgresql://dpg-abc123-a:5432/opsdb` | host + port 5432 + database name |
   | `DB_USER` | `ops` | before the `:` in the userinfo |
   | `DB_PASSWORD` | `AbC123xyz` | after the `:` in the userinfo |

   > **Format matters:** the backend needs a **JDBC** URL. `postgresql://…` will fail with
   > "No suitable driver". It must start with `jdbc:postgresql://`. Credentials go in
   > `DB_USER` / `DB_PASSWORD`, not in the URL.

### 1.2 Create the backend web service (~8 min first build)

1. **New → Web Service** → connect your GitHub repo
   (`Luyolo23/business_operations_platform`).
2. Fill in:

   | Field | Value |
   |---|---|
   | Name | `ops-backend` (this becomes `https://ops-backend.onrender.com`) |
   | Region | **same as the database** |
   | Language / Environment | **Docker** |
   | Dockerfile Path | `./backend/Dockerfile` |
   | Docker Context Directory | `./backend` |
   | Instance Type | Free (or Starter) |
   | Health Check Path | `/actuator/health` |
   | Auto-Deploy | Yes (deploys on every push to `main`) |

3. Add the environment variables:

   | Key | Value |
   |---|---|
   | `JWT_SECRET` | Click **Generate** (or paste 32+ random bytes). **Required** — the app refuses to start without it. |
   | `DB_URL` | `jdbc:postgresql://dpg-abc123-a:5432/opsdb` |
   | `DB_USER` | `ops` |
   | `DB_PASSWORD` | the password from step 1.1 |
   | `APP_SEED_ENABLED` | `true` **for the first boot only** |
   | `APP_SEED_DEMO_TICKETS` | `false` |
   | `CORS_ORIGINS` | `https://*.vercel.app` (add your custom domain in Part 2) |
   | `AI_PROVIDER` | `mock` (offline, deterministic) or `llm` + `AI_API_KEY` |

   Do **not** set `PORT` (Render injects it) and do **not** set `SPRING_PROFILES_ACTIVE`
   (the `dev` profile enables Swagger, demo pills and demo tickets).

4. **Create Web Service** and watch the logs. A good first boot contains:

   ```
   Successfully applied 7 migrations to schema "public"
   Seeded demo users (password 'Passw0rd!') ...
   Started OpsApplication in ... seconds
   ```

5. Test the backend directly:

   ```bash
   curl -fsS https://ops-backend.onrender.com/actuator/health   # {"status":"UP"}
   curl -fsS https://ops-backend.onrender.com/api/config        # {"demoMode":false}
   ```

   Free instances sleep after ~15 minutes idle. The first request afterwards takes
   ~45–60 seconds — if curl times out, retry once.

### 1.3 Bootstrap your real accounts, then remove the demo ones

Do this **after** Part 2, when you can log in through the UI. The flow is exactly
[`docs/DATA_CLEANUP.md`](DATA_CLEANUP.md) Path A:

1. Log in as `admin@ops.local` / `Passw0rd!`.
2. **People → New account** — create your real ADMIN, MANAGER and AGENT accounts.
3. Log in as your real admin and **delete** the seven `@ops.local` accounts.
4. On Render, set `APP_SEED_ENABLED=false` and save (the service restarts).

If you prefer not to log in as the demo admin at all, use Path B of that document instead.

---

## Part 2 — Frontend on Vercel

### 2.1 Point the proxy at your backend

Edit the one placeholder line in `frontend/vercel.json`:

```json
{
  "source": "/api/:path*",
  "destination": "https://ops-backend.onrender.com/api/:path*"
}
```

Commit and push:

```bash
git add frontend/vercel.json
git commit -m "chore(deploy): point Vercel /api proxy at the Render backend"
git push
```

### 2.2 Import the project

1. Sign in at <https://vercel.com> → **Add New → Project** → import the same GitHub repo.
2. **Edit** the *Root Directory* and choose **`frontend`**.
   This is the step people miss — the repo root has no `package.json`.
3. Framework Preset: **Vite** (auto-detected). Build Command `npm run build`, Output
   Directory `dist` come from `vercel.json`; Install Command is `npm ci` automatically.
4. Environment variables: **none**. The frontend calls relative `/api/...` paths; there is
   no API URL baked into the bundle.
5. **Deploy**. You get `https://<project>.vercel.app`.

### 2.3 Allow the Vercel domain on the backend

The browser talks to Vercel, and Vercel proxies `/api/*` to Render — but it still forwards
the `Origin: https://<project>.vercel.app` header, and Spring rejects unknown origins with
`403 Invalid CORS request`. So add your domains on Render:

```
CORS_ORIGINS=https://<project>.vercel.app,https://*.vercel.app
```

The `*.vercel.app` pattern covers every preview deployment. Saving the env var restarts the
service automatically. Add your custom domain to the same list after step 2.4.

### 2.4 Custom domain (optional)

Vercel → Project → **Settings → Domains → Add**, then point your DNS as instructed. Add
`https://your-domain.com` to `CORS_ORIGINS` on Render.

---

## Part 3 — Verify the whole thing

```bash
# Backend health directly
curl -fsS https://ops-backend.onrender.com/actuator/health

# Through the Vercel proxy — must return {"demoMode":false}
curl -fsS https://<project>.vercel.app/api/config

# Login through the proxy — must return a token
curl -fsS -X POST https://<project>.vercel.app/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"your.admin@company.com","password":"..."}' | head -c 120

# Deep link must serve the app (HTML), not a 404
curl -fsS -o /dev/null -w '%{http_code}\n' https://<project>.vercel.app/tickets
```

In the browser:

- Login page loads with no demo-account pills.
- Logging in shows the dashboard (zeros on a fresh database).
- Creating a ticket classifies and routes it.
- Refresh on `/tickets` keeps you on the page (SPA rewrite working).
- No `Invalid CORS request` in the backend log.

---

## Costs and limits

| Piece | Free tier | Watch out for |
|---|---|---|
| Vercel Hobby | Free | Hobby is for **non-commercial** projects. A business needs Pro (~$20/month). |
| Render web service (free) | Free | Sleeps after ~15 min idle; cold start ~50 s. Vercel's proxy may time out on the first request — retry, or use a paid instance. |
| Render Postgres (free) | Free | Free databases are **time-limited** (Render deletes them after the free period). For data you want to keep, use a paid database or Neon. |
| Neon Postgres (alternative) | Free, no expiry | Connection string needs `?sslmode=require`. |
| Custom domain | Paid | ~$10–15/year. |

A demo for a marketer costs R0. For a real deployment, budget roughly $7–15/month for the
backend and database, plus Vercel Pro if it is commercial.

---

## Common problems

| Symptom | Cause and fix |
|---|---|
| Vercel build fails: `No package.json found` | Root Directory was left at the repo root. Set it to `frontend`. |
| `/api/...` returns **502** / **504** | Backend is asleep or still starting. Open `/actuator/health` directly and wait; the free tier needs a retry after idle. |
| `403 Invalid CORS request` in the backend log | The Vercel domain is missing from `CORS_ORIGINS`. Add `https://<project>.vercel.app` and redeploy. |
| Refresh on `/tickets` gives 404 | `frontend/vercel.json` was not deployed (wrong root directory) or the SPA rewrite was removed. |
| Backend exits: `Could not resolve placeholder 'JWT_SECRET'` / secret too short | `JWT_SECRET` missing or under 32 bytes. Generate a new one on Render. |
| Backend exits: `No suitable driver found for postgresql://...` | `DB_URL` is not a JDBC URL. Use `jdbc:postgresql://host:5432/db`. |
| `Connection refused` to the database | Web service and database are in different regions, or you used the **external** host from inside Render. Use the Internal Database URL. |
| Login returns `401` for the demo accounts | Expected if you already ran the cleanup. Use your real admin. |
| Flyway `checksum mismatch` | An applied `V*.sql` was edited. Revert it; add a new migration instead. |
| Every deploy shows old UI | The commit was not pushed, or Vercel is building a different branch. Check the deployment's commit hash. |

---

## Alternatives

- **Railway / Fly.io** instead of Render: both accept the same Dockerfile and inject `PORT`.
  Set the same `DB_*`, `JWT_SECRET`, `CORS_ORIGINS` variables. Railway's Postgres plugin
  gives you `PGHOST` / `PGPORT` / `PGUSER` / `PGPASSWORD` / `PGDATABASE` to build `DB_URL`.
- **One server instead of Vercel**: `docs/DEPLOYMENT.md` runs all three containers with one
  command (`docker compose -f docker-compose.full.yml up -d --build`). If the requirement is
  a single host, use that; Vercel is the better choice when you want the frontend on a
  global CDN and the backend managed separately.

## Checklist before you call it deployed

- [ ] `JWT_SECRET` generated and stored only in Render
- [ ] `DB_URL` is a JDBC URL, database reachable from the service's region
- [ ] `SPRING_PROFILES_ACTIVE` unset, `APP_SEED_DEMO_TICKETS=false`
- [ ] Real admin/manager/agents created; all `@ops.local` accounts deleted
- [ ] `APP_SEED_ENABLED=false` after the bootstrap
- [ ] `CORS_ORIGINS` contains the Vercel production domain (and `https://*.vercel.app` if you use previews)
- [ ] `frontend/vercel.json` points at the real backend host
- [ ] `/api/config` through Vercel returns `{"demoMode":false}`
- [ ] A ticket can be created, classified and replied to in the deployed UI
- [ ] Nightly database backup (Render backups or `pg_dump`) copied off-host
