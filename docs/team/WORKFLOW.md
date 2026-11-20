# Team Workflow — how each member contributes

> **Status:** all tasks in this folder are now implemented on `main`. The briefs are kept
> as the record of how the work was split between the four roles. Build/lint/test
> commands below still apply.

This file is for all four team members. Read it once, then open your own task file:

| Person | Role | Task file |
|--------|------|-----------|
| Luyolo | Backend lead | `docs/team/MAINTAINER_MERGE.md` |
| — | Frontend | `docs/team/FRONTEND_TASKS.md` |
| — | AI / classifier | `docs/team/AI_TASKS.md` |
| — | Database | `docs/team/DATABASE_TASKS.md` |

The ownership map is also in `docs/TEAM_GUIDE.md`.

---

## 1. One-time setup (every member)

You need to be a collaborator on the GitHub repo, and your commit email must be the
email on your GitHub account. Otherwise the commit exists but GitHub will not
attribute it to you.

```bash
git config --global user.name  "Your Full Name"
git config --global user.email "the-email-on-your-github-account@example.com"
```

Check it:

```bash
git config user.name
git config user.email
```

On GitHub: **Settings → Emails** — confirm that address is listed there.

---

## 2. Start work: always branch from an up-to-date `main`

`main` is the default branch and the only place contributions are counted.

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feat/<short-description>
```

Examples: `feat/approvals-tabs`, `feat/loan-finance-keywords`, `feat/hot-path-indexes`.

> `features/frontend_setup` is old (it is behind `main`). Do not keep building on it —
> create a fresh branch from `main` as shown above.

---

## 3. Make a small, complete change

- Stay inside the files your role owns (see the task file).
- Keep the change small enough to explain in one sentence.
- Update docs when behaviour changes.
- Never commit `.env`, API keys, `node_modules/`, `target/` or `dist/`.

Check what you are about to commit:

```bash
git status
git diff
```

---

## 4. Commit with Conventional Commit messages

One logical change per commit:

```bash
git add <files>
git commit -m "fix(ai): classify loan requests as FINANCE"
```

Prefixes used in this repo: `feat:`, `fix:`, `docs:`, `test:`, `ci:`, `chore:`.
The scope in brackets is optional but useful: `(ai)`, `(ui)`, `(db)`, `(security)`, `(ticket)`.

---

## 5. Verify before pushing

| Area | Command | Needs |
|------|---------|-------|
| Backend unit tests | `make test` | nothing |
| Backend integration tests | `make it` | Docker |
| Frontend build | `cd frontend && npm ci && npm run build` | Node 22 |
| Frontend lint | `cd frontend && npm run lint` | Node 22 |

If you touched SQL, also start the app once so Flyway applies your migration:

```bash
make up        # Postgres on localhost:5433
make backend   # Spring Boot with Flyway on startup
```

Flyway is strict: **a failed or edited migration blocks startup**. Test it locally first.

---

## 6. Push the branch and open a pull request into `main`

```bash
git push -u origin feat/<short-description>
```

Then on GitHub:

1. **Pull requests → New pull request**
2. **base:** `main`  **compare:** `feat/<short-description>`
3. Title = your commit message, description = what changed and how you tested it
4. Create pull request
5. Wait for CI to go green (backend unit tests, frontend build, Docker build)

> If every job fails within seconds and shows no steps, that is the GitHub billing
> lock, not your code — see `docs/team/MAINTAINER_MERGE.md`. Run the checks locally
> (`make test`, `npm run build`) and ask the backend lead to re-run CI once billing is fixed.

6. **Squash and merge** → confirm

Once merged, the commit lands on `main` and shows in your contribution history.

---

## 7. Rules that prevent broken builds

1. **Only the Database owner adds migrations.** Everyone else asks them. Never edit
   an existing `V*.sql` file — Flyway checksums it. New file = next number.
2. **Do not change another owner's files silently.** Coordinate first.
   - Shared: `frontend/src/api/types.ts`, `frontend/src/api/endpoints.ts`,
     `backend/pom.xml`, `SecurityConfig.java`, `docs/`.
3. **Never rewrite `main`.** No `git push --force` to `main`.
4. **A PR is not done until CI is green.**

---

## 8. Useful commands

```bash
git log --oneline --graph --decorate -15   # see where you are
git diff main...HEAD                        # everything your branch adds
git status                                  # what is staged / dirty
git switch main && git pull                 # get everyone else's merged work
git rebase origin/main                      # replay your branch on latest main (optional)
```
