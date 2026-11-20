# Removing Demo Data — tickets and old accounts

Use this after a demo, or before going live, when you want the database to contain
**only your real people**: your ADMIN, your MANAGER(s) and your AGENT(s) — with no
seeded tickets and no `@ops.local` accounts.

The demo data is exactly this:

| What | How to recognise it |
|------|---------------------|
| 7 demo accounts | emails ending in `@ops.local`: `admin@`, `manager@`, `agent.it@`, `agent.hr@`, `agent.finance@`, `agent.ops@`, `employee@` |
| 80 demo tickets (only if `APP_SEED_DEMO_TICKETS=true` or `SPRING_PROFILES_ACTIVE=dev`) | every one was raised by `employee@ops.local` |
| Demo password | `Passw0rd!` for all seven accounts |

> **Read this first:** there is **no delete-ticket endpoint**. Tickets can only be removed
> with SQL against PostgreSQL (Path B), or by wiping the database volume (Path A). Accounts
> *can* be deleted from **People** in the UI, but only once they have no ticket history.

Pick one path:

| Path | Use when | Result |
|------|----------|--------|
| **A — Reset the volume** | The database contains nothing but demo data | Empty DB, then you bootstrap only the accounts you want. Fastest and cleanest. |
| **B — Surgical SQL cleanup** | You already have real tickets/accounts you must keep | Demo tickets and demo accounts removed, everything else untouched. |
| **C — Keep history, block access** | You must keep tickets a demo account raised or handled | Accounts stay in the DB but can no longer log in. |

### Which database are you cleaning?

| Database | Started with | psql | Reset the volume |
|----------|--------------|------|------------------|
| Dev database (port 5433) | `docker compose up -d` | `docker compose exec -T postgres psql -U ops -d opsdb` | `docker compose down -v` |
| Full stack (port 8081) | `docker compose -f docker-compose.full.yml up -d` | `docker compose -f docker-compose.full.yml exec -T postgres psql -U ops -d opsdb` | `docker compose -f docker-compose.full.yml down -v` |

The commands below use the **full stack**; substitute the dev row if that is the database
you mean. The dev database is seeded with 80 tickets whenever the `dev` profile runs.

---

## Path A — Start from an empty database

This deletes **everything** in the database (all tickets, responses, approvals,
notifications and accounts). It is the right choice when the only data is demo data.

```bash
cd /home/luyolo/business_operations_platform

# 1. Stop the stack and delete the Postgres volume (ops-full_pgdata)
docker compose -f docker-compose.full.yml down -v

# 2. Boot with the seeder on but demo tickets OFF, so you get accounts to log in with
APP_SEED_ENABLED=true APP_SEED_DEMO_TICKETS=false \
  docker compose -f docker-compose.full.yml up -d

# 3. Wait for health
docker compose -f docker-compose.full.yml ps
```

The seeder creates the 7 `@ops.local` accounts and **zero tickets**. Log in at
`http://localhost:8081` as `admin@ops.local` / `Passw0rd!`, then:

1. **People → New account** — create your real ADMIN, MANAGER and AGENT accounts.
   - Agents must have a department (IT / HR / FINANCE / OPERATIONS).
   - Use the password policy minimum: 8 characters.
2. Log out, log in as **your new admin**.
3. Back in **People**, delete the seven `@ops.local` accounts with the **Delete** button.
   This works now because they have no ticket history. The last-active-admin guard is
   satisfied by your real admin, and the API refuses to let you delete your own account.
4. Turn the seeder off so it never creates demo accounts again:

```bash
APP_SEED_ENABLED=false docker compose -f docker-compose.full.yml up -d --force-recreate backend
```

5. Verify (see [Verification](#verification)): 1 admin + 1 manager + your agents, 0 tickets.

> If you skip step 2 and delete the demo admin first, the database has **no admin** and
> you cannot create accounts. The seeder is idempotent (`if users.count() > 0 return`), so
> re-running it will *not* help once any user exists.

---

## Path B — Keep the database, delete only the demo data

Use this when real accounts and/or real tickets already exist. **Take a backup first.**

```bash
cd /home/luyolo/business_operations_platform

# Backup
docker compose -f docker-compose.full.yml exec -T postgres \
  pg_dump -U ops -d opsdb | gzip > "opsdb-before-cleanup-$(date +%F-%H%M).sql.gz"
```

### Step 1 — Create your real accounts first (UI or API)

Log in as the demo admin (`admin@ops.local` / `Passw0rd!`) and use **People → New account**
to create your real ADMIN, MANAGER and AGENT accounts. Do not delete anything yet.

The SQL below **aborts on purpose** if no active, non-`@ops.local` admin exists, so you
cannot lock yourself out by accident.

### Step 2 — Run the cleanup SQL

Save this as `cleanup.sql` on the server, then run it. The order matters: child rows
(`risk_assessments`, `approvals`, `ticket_responses`, `notifications`) reference
`tickets`, and `tickets` references `users`.

```sql
BEGIN;

-- Abort unless a real (non-demo) admin already exists.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users
        WHERE role = 'ADMIN' AND active AND email NOT LIKE '%@ops.local'
    ) THEN
        RAISE EXCEPTION 'Create your real ADMIN account first; refusing to delete the demo admin.';
    END IF;
END $$;

-- 1. Demo tickets = everything raised by the seeded employee.
CREATE TEMP TABLE demo_ticket_ids ON COMMIT DROP AS
SELECT id FROM tickets
WHERE requester_id IN (SELECT id FROM users WHERE email = 'employee@ops.local');

-- 2. Delete child rows first.
DELETE FROM risk_assessments
WHERE response_id IN (SELECT id FROM ticket_responses
                      WHERE ticket_id IN (SELECT id FROM demo_ticket_ids));

DELETE FROM approvals
WHERE response_id IN (SELECT id FROM ticket_responses
                      WHERE ticket_id IN (SELECT id FROM demo_ticket_ids));

DELETE FROM ticket_responses
WHERE ticket_id IN (SELECT id FROM demo_ticket_ids);

-- notifications.ticket_id is ON DELETE SET NULL; remove them explicitly so no
-- orphaned "Ticket #73 ..." notifications survive.
DELETE FROM notifications
WHERE ticket_id IN (SELECT id FROM demo_ticket_ids);

DELETE FROM tickets
WHERE id IN (SELECT id FROM demo_ticket_ids);

-- 3. Demo accounts. They have no history any more, so the FKs allow the delete.
DELETE FROM notifications
WHERE user_id IN (SELECT id FROM users WHERE email IN (
    'admin@ops.local', 'manager@ops.local',
    'agent.it@ops.local', 'agent.hr@ops.local',
    'agent.finance@ops.local', 'agent.ops@ops.local',
    'employee@ops.local'
));

DELETE FROM users WHERE email IN (
    'admin@ops.local', 'manager@ops.local',
    'agent.it@ops.local', 'agent.hr@ops.local',
    'agent.finance@ops.local', 'agent.ops@ops.local',
    'employee@ops.local'
);

COMMIT;
```

```bash
docker compose -f docker-compose.full.yml exec -T postgres \
  psql -U ops -d opsdb -v ON_ERROR_STOP=1 < cleanup.sql
```

Expected output ends with `COMMIT` (the `DELETE 80` line confirms the demo tickets).

### Step 3 — Turn the seeder off

```bash
APP_SEED_ENABLED=false docker compose -f docker-compose.full.yml up -d --force-recreate backend
```

`APP_SEED_ENABLED` already defaults to `false` in `docker-compose.full.yml`; this is only
needed if you previously started the stack with it set to `true`.

### If the delete fails with a foreign-key error

Something outside the demo employee's tickets still references a demo account. Find it:

```sql
-- Tickets requested by a demo account
SELECT id, title, status FROM tickets
WHERE requester_id IN (SELECT id FROM users WHERE email LIKE '%@ops.local');

-- Tickets assigned to a demo account
SELECT id, title, status FROM tickets
WHERE assignee_id IN (SELECT id FROM users WHERE email LIKE '%@ops.local');
```

Either delete those tickets with the same child-first pattern, or use **Path C** for that
account. The same applies to replies written by a demo agent (`ticket_responses.created_by`)
and approval decisions (`approvals.decided_by`).

The whole script runs in one transaction, so a failure **rolls back completely** — you are
never left half-cleaned.

---

## Path C — Keep the history, disable the accounts

Use this when a demo account is referenced by tickets you want to keep. Deactivation keeps
every ticket, reply and approval intact and simply blocks login.

In **People**, press **Switch off** next to the account, or via the API:

```bash
# admin token
TOKEN=$(curl -fsS -X POST http://localhost:8081/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"your.admin@company.com","password":"..."}' | jq -r .token)

# list ids/emails
curl -fsS http://localhost:8081/api/users -H "authorization: Bearer $TOKEN" \
  | jq -r '.[] | "\(.id)\t\(.email)\t\(.role)\t\(.active)"'

# deactivate one (works on @ops.local accounts)
curl -fsS -X PATCH http://localhost:8081/api/users/<id>/deactivate \
  -H "authorization: Bearer $TOKEN"
```

Guards that apply: you cannot deactivate yourself, and you cannot deactivate the **last
active admin**. Deactivated accounts keep their name on old tickets but cannot log in.

To hide demo tickets from the dashboards without deleting them, you can instead delete only
the *tickets* (step 2 of the Path B script, skip step 3) and deactivate the accounts.

---

## Verification

Run these after any path.

```bash
# Counts: expect only your real staff, and zero tickets
docker compose -f docker-compose.full.yml exec -T postgres psql -U ops -d opsdb -tAc "
  SELECT (SELECT count(*) FROM users)       || ' users, ' ||
         (SELECT count(*) FROM tickets)     || ' tickets, ' ||
         (SELECT count(*) FROM ticket_responses) || ' responses, ' ||
         (SELECT count(*) FROM approvals)   || ' approvals';"
```

```bash
# Accounts, roles and departments
docker compose -f docker-compose.full.yml exec -T postgres psql -U ops -d opsdb \
  -c "SELECT id, email, role, department, active FROM users ORDER BY role, email;"
```

Then in a browser or with curl:

| Check | Expected |
|-------|----------|
| Log in as your real admin | succeeds |
| Log in as `admin@ops.local` | `401` |
| `GET /api/users` (as admin) | only your real accounts |
| `GET /api/users/staff` (as manager/admin) | your manager + agents only |
| `GET /api/tickets` | `{"content":[], ...}` (empty page) |
| Dashboard | loads with zeros, no errors |
| `GET /api/config` | `{"demoMode":false}` |

```bash
TOKEN=$(curl -fsS -X POST http://localhost:8081/api/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"your.admin@company.com","password":"..."}' | jq -r .token)

curl -fsS http://localhost:8081/api/users       -H "authorization: Bearer $TOKEN" | jq -c '[.[]|.email]'
curl -fsS http://localhost:8081/api/users/staff -H "authorization: Bearer $TOKEN" | jq -c '[.[]|.email]'
curl -fsS http://localhost:8081/api/tickets     -H "authorization: Bearer $TOKEN" | jq -c '{count:(.content|length)}'
```

---

## What is kept

These are **not** demo data and are left untouched by every path:

- `routing_rules` (category → department + SLA) — from `V2`
- `response_templates` — from `V2`
- `audit_logs` — the audit trail, including the USER_CREATED / USER_DELETED entries
- Flyway's `flyway_schema_history`
- Any account you created and any ticket not raised by `employee@ops.local`

Path A wipes them along with the volume; Flyway re-creates `routing_rules`,
`response_templates` and the schema on the next boot.

---

## Rolling back

Path B's SQL is transactional, and you took a `pg_dump` first. To restore:

```bash
docker compose -f docker-compose.full.yml down
gunzip -c opsdb-before-cleanup-YYYY-MM-DD-HHMM.sql.gz | \
  docker compose -f docker-compose.full.yml exec -T postgres psql -U ops -d opsdb
docker compose -f docker-compose.full.yml up -d
```

Path A is not reversible — the volume is gone. That is why it is only for databases
containing demo data.

---

## Notes and caveats

- **SQL bypasses the audit log.** Deleting tickets with `psql` writes nothing to
  `audit_logs`. Account deletions done through **People** (or `DELETE /api/users/{id}`)
  are audited. If you need the audit trail to show the cleanup, delete the accounts
  through the UI after running the ticket part of the script.
- **The API refuses to delete users with history** by design
  (`UserController.delete()`), which is why the ticket deletion must happen first.
- **Demo tickets are recognised by requester**, not by title. Anything a real user raised
  while logged in as `employee@ops.local` would also be deleted — check the count
  (`DELETE 80` for an untouched demo database) before committing.
- **No password change endpoint.** Deactivating or deleting the demo accounts is the only
  way to retire the public `Passw0rd!` password.
- `docker compose down -v` in Path A deletes the named volume `ops-full_pgdata`; it does not
  touch backups you copied elsewhere.
