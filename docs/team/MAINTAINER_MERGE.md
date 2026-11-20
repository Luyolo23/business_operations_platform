# Maintainer: merge your branch into `main`

This is only for Luyolo (backend lead).

> **Status: done.** `features/backend_setup` was merged into `main` in **PR #2**
> (merge commit `05a2ba4`), and the database work followed in **PR #21** (`67932ba`).
> Keep this file as the runbook for the next long-lived branch — the steps are still
> correct and were verified with a real trial merge. The remaining blocker is the
> GitHub billing lock described at the bottom, not git.

State of the repo after those merges:

| Ref | Commit | Notes |
|-----|--------|-------|
| `main` | `67932ba` | default branch; backend + frontend + database work merged |
| `origin/main` | `67932ba` | in sync |
| `features/backend_setup` | `aac52fa` | kept for history; already contained in `main` |
| `backup/backend-setup-pre-merge` | `8ddc76a` | pre-merge safety snapshot |
| `features/frontend_setup` | `c3af7e4` | stale: 1 commit ("react init"), behind `main` |

When this runbook was first written, `main` was `b98ab15` and had no `frontend/`
directory. The merge introduced it and there was exactly one conflict — `README.md`
(both sides added it). Resolve that conflict by keeping the branch's 315-line README.

Everything else auto-merged. I verified this with a real trial merge before writing
these steps.

---

## Step 0 — commit these task briefs first

```bash
git add docs/team
git commit -m "docs(team): add per-role task briefs and merge runbook"
```

---

## Option A (recommended) — update your branch, then open a PR

A pull request means CI runs and GitHub records the merge cleanly.

### Step 1 — bring `main` into your branch and fix the one conflict

```bash
cd /home/luyolo/business_operations_platform

# safety net: snapshot the current tip before touching anything
git branch backup/backend-setup-pre-merge

git switch features/backend_setup
git fetch origin
git merge origin/main
```

Git stops with:

```
CONFLICT (add/add): Merge conflict in README.md
```

While you are ON `features/backend_setup`, merges are labelled like this:

- **ours** = `features/backend_setup` (your branch, the good README)
- **theirs** = `origin/main` (the stub README)

So keep yours:

```bash
git checkout --ours README.md
git add README.md
git commit --no-edit
```

Confirm nothing else is unmerged:

```bash
git status                 # should say "nothing to commit, working tree clean"
git diff --name-only --diff-filter=U   # must print nothing
```

### Step 2 — verify before pushing

```bash
make test
cd frontend && npm ci && npm run build && cd ..
```

Both must succeed. If `npm ci` fails because of the lock file, run `npm install` and
commit the updated `package-lock.json`.

### Step 3 — push your branch

```bash
git push origin features/backend_setup
```

### Step 4 — open and merge the PR on GitHub

1. Open the repo → **Pull requests** → **New pull request**
2. **base:** `main` ← **compare:** `features/backend_setup`
3. Title: `feat: backend, security, AI workflow and database implementation`
4. Description: summarise the areas and the test commands you just ran
5. **Create pull request**
6. Wait for all CI jobs to pass (Backend unit tests, Backend integration tests,
   Frontend build, Docker images)
7. **Merge pull request** → **Confirm merge**
8. Delete the branch only if you no longer need it (it is the backup)

### Step 5 — sync your local `main`

```bash
git switch main
git pull --ff-only origin main
git log --oneline -5          # the merge commit must be there
```

`main` now contains all 46 commits. Your GitHub contribution graph updates after a few
minutes (sometimes a hard refresh is needed).

---

## Option B — merge directly on `main` (no PR)

Only if you cannot use a PR. Same result, but no CI gate.

```bash
cd /home/luyolo/business_operations_platform

git branch backup/backend-setup-pre-merge

git switch main
git pull --ff-only origin main
git merge --no-ff features/backend_setup
```

This direction flips **ours/theirs**:

- **ours** = `main` (stub README)
- **theirs** = `features/backend_setup` (your README)

So keep theirs this time:

```bash
git checkout --theirs README.md
git add README.md
git commit --no-edit

make test
cd frontend && npm ci && npm run build && cd ..

git push origin main
```

> Git's `--ours`/`--theirs` naming is about the branch you are standing on when the
> merge starts, not about which content is newer. Read the two blocks above carefully;
> they are deliberately different.

---

## If something goes wrong

Abort a merge before committing:

```bash
git merge --abort
```

Throw away a bad merge that you already committed locally but did **not** push:

```bash
git switch main
git reset --hard origin/main
```

Restore the pre-merge snapshot:

```bash
git switch features/backend_setup
git reset --hard backup/backend-setup-pre-merge
```

Never `git push --force` to `main`. If a bad commit was already pushed, revert it:

```bash
git switch main && git pull --ff-only
git revert <bad-commit-sha>
git push origin main
```

---

## After `main` is updated — hand out the work

Tell every teammate exactly this:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feat/<their task branch>
```

Then point them at their file:

- `docs/team/FRONTEND_TASKS.md`
- `docs/team/AI_TASKS.md`
- `docs/team/DATABASE_TASKS.md`

`features/frontend_setup` is stale. The frontend teammate should **not** continue on it.
Either start a fresh branch from `main` (recommended) or rebase:

```bash
git switch features/frontend_setup
git rebase origin/main
```

If the rebase produces conflicts in files that do not exist on that old branch,
`git rebase --abort` and just branch fresh from `main`.

---

## Reviewing and merging their PRs

For each teammate PR:

1. **Files changed** tab — confirm they only touched their own area.
2. **Checks** tab — all jobs green.
3. If SQL changed: read the migration; it must be a new `V6`/`V7` file, never an edit
   to `V1`–`V5`.
4. Merge with **Squash and merge** so each task becomes one clean commit on `main`.
   Use a Conventional Commit title, e.g. `fix(ai): classify loan requests as FINANCE`.
5. Delete the branch after merge.

Squash merging keeps the original author on the commit, so the contribution still
counts for them. A merge commit also works, but the history becomes noisy.

### Attribution checklist

- The commit email matches their GitHub account email (Settings → Emails).
- The PR was merged into `main` — commits that only exist on an unmerged branch do
  not appear on a profile.
- The commit author is them, not you — do not commit their work for them.

---

## Why GitHub CI may fail even when the code is fine

If every job fails in 2–4 seconds with no log steps, click a failed job and read the
annotation. The repo hit this exact state between 9 and 20 November 2026:

> The job was not started because your account is locked due to a billing issue.

That is a GitHub **account** problem, not a code problem. Fix it in
**GitHub → Settings → Billing & plans** (payment method or spending limit), then open
**Actions → CI → Run workflow** (the workflow now supports `workflow_dispatch`). Until
then, run the same checks locally:

```bash
make test
make it
cd frontend && npm ci && npm run build
docker build -t ops-backend ./backend
docker build -t ops-frontend ./frontend
```

---

## One-time cleanup you may want

After the merge, `features/backend_setup` and `main` are equivalent. You can keep
`backup/backend-setup-pre-merge` for a week, then delete it:

```bash
git branch -d backup/backend-setup-pre-merge
git push origin --delete features/frontend_setup   # only after the frontend dev moves off it
```
