# Team Guide

## Who Owns What

| Role | Area | Key files |
|------|------|-----------|
| **Backend lead** | Spring Boot core, security, CI | `backend/src/main/java/com/capaciti/ops/security/` |
| **Database owner** | Flyway migrations, schema, indexes | `backend/src/main/resources/db/migration/` |
| **Frontend dev** | React pages, components, API types | `frontend/src/` |
| **AI / ML** | Classifiers, response generators, prompts | `backend/src/main/java/com/capaciti/ops/ai/` |

> **Golden rule:** Only the Database Owner adds or modifies migration files. Everyone else adds new `V<n>__*.sql` files — never edits an existing one (Flyway checksums are immutable).

---

## Branching Convention

```
main           ← production-ready
 └─ dev        ← integration branch
     ├─ feat/week-1-classifier
     ├─ feat/week-2-response-generator
     └─ fix/duplicate-email-409
```

- Branch names: `feat/<short-description>` or `fix/<short-description>`
- Open a PR into **dev**, get at least one review, then merge
- `main` is only updated at sprint boundaries after full testing

---

## How to Add a New Feature Module

1. Create a package `com.capaciti.ops.<module>/`
2. Add at minimum: `Entity.java`, `Repository.java`, `Service.java`, `Controller.java`, `Dtos.java`
3. If you need a new DB table, ask the Database Owner to add `V<next>__<description>.sql`
4. Add security rules in `SecurityConfig`
5. Add a frontend page in `frontend/src/pages/` and wire it in `App.tsx`
6. Write unit tests in `backend/src/test/java/com/capaciti/ops/<module>/`

---

## Adding a New Endpoint — Checklist

- [ ] Controller method added with correct HTTP method and path
- [ ] SecurityConfig updated with the right role restriction
- [ ] Input validated with `@Valid` and JSR-380 annotations
- [ ] Error cases throw the appropriate custom exception (`NotFoundException`, `BadRequestException`, `ForbiddenException`)
- [ ] Frontend API client updated in `frontend/src/api/endpoints.ts`
- [ ] TypeScript types updated in `frontend/src/api/types.ts`
- [ ] Unit test added (or integration test if it touches the DB)

---

## Secrets & API Keys

| Secret | Where | How to set |
|--------|-------|-----------|
| `JWT_SECRET` | `.env` / Docker env / CI secret | `openssl rand -base64 48` |
| `AI_API_KEY` | `.env` / Docker env / CI secret | Get from [Groq](https://console.groq.com/keys) or [OpenAI](https://platform.openai.com/api-keys) |
| `DB_PASSWORD` | `.env` / Docker env / CI secret | Any strong password |

**Never hard-code secrets.** Use `.env` locally, Docker `--env-file .env` for compose, and GitHub Secrets for CI.

---

## Running the Full Stack Locally

```bash
cp .env.example .env      # fill in JWT_SECRET at minimum
make full                 # docker compose -f docker-compose.full.yml up --build
```

Open **http://localhost:8081** for the frontend or **http://localhost:8080/swagger-ui.html** for the API.

---

## CI (GitHub Actions — to be configured)

Recommended workflow jobs:

| Job | Command |
|-----|---------|
| Unit tests | `cd backend && mvn -q test -DexcludedGroups=integration` |
| Integration tests | `cd backend && mvn -q test -Dgroups=integration` (Docker required) |
| Frontend build | `cd frontend && npm ci && npm run build` |
| Docker build | `docker compose -f docker-compose.full.yml build --no-cache` |
