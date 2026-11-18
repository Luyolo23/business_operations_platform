# Frontend Tasks

Owner: Frontend developer. Everything here lives under `frontend/src/` except the
one CSS block, which also lives there.

Read `docs/team/WORKFLOW.md` first. Branch off `main`:

```bash
git fetch origin
git switch main && git pull --ff-only origin main
git switch -c feat/approvals-tabs
```

Do the tasks in order. Task 1 and 2 are the ones that give you the biggest, safest
diff. Task 3 is tooling and should be its own branch (`feat/frontend-lint`).

---

## Task 1 — Approvals page: Pending / Approved / Rejected tabs

**Why:** the backend already supports `GET /api/approvals?status=APPROVED|REJECTED`
(`ApprovalService.list`), but `ApprovalsPage.tsx` hard-codes `'PENDING'` and throws
away the decision history. Managers currently cannot see what was approved or rejected.

**Files**

- `frontend/src/pages/ApprovalsPage.tsx` (replace the whole file)
- `frontend/src/index.css` (add the small `.tabs` block at the end)

`frontend/src/api/endpoints.ts` and `frontend/src/api/types.ts` already have
everything needed (`approvalApi.list(status)` and the `ApprovalStatus` type), so do
**not** change them.

### Full replacement for `frontend/src/pages/ApprovalsPage.tsx`

```tsx
import { useRef, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { approvalApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { useAsync } from '../components/useAsync';
import { Empty, Notice, Panel, RiskMark, Status, fmtDate } from '../components/ui';
import { RISK_COLOR, flagText } from '../theme';
import type { ApprovalStatus } from '../api/types';

const TABS: { value: ApprovalStatus; label: string }[] = [
  { value: 'PENDING', label: 'Waiting for you' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

const LEDE: Record<ApprovalStatus, string> = {
  PENDING: 'The assistant holds back any drafted reply that might promise too much, blame the requester or include personal data.',
  APPROVED: 'Replies a manager approved. Approved replies have been delivered to the requester.',
  REJECTED: 'Replies a manager sent back to the agent for a rewrite. They were not delivered.',
};

const EMPTY: Record<ApprovalStatus, { title: string; body: string }> = {
  PENDING: { title: 'All clear.', body: 'New replies that need a second pair of eyes will appear here.' },
  APPROVED: { title: 'Nothing approved yet.', body: 'Replies a manager approves will be listed here.' },
  REJECTED: { title: 'Nothing rejected yet.', body: 'Replies a manager sends back to an agent will be listed here.' },
};

export default function ApprovalsPage() {
  const [tab, setTab] = useState<ApprovalStatus>('PENDING');
  const { data, loading, error, reload } = useAsync(() => approvalApi.list(tab), [tab]);
  const [comments, setComments] = useState<Record<number, string>>({});
  const [result, setResult] = useState<{ tone: 'good' | 'warn'; text: string } | null>(null);
  const [deciding, setDeciding] = useState<number | null>(null);
  const inFlight = useRef(false);

  async function decide(id: number, approve: boolean) {
    // A fast double click (or approving and rejecting the same card) would
    // otherwise send two decisions and race the version check.
    if (inFlight.current) return;
    inFlight.current = true;
    setDeciding(id);
    setResult(null);
    try {
      await approvalApi.decide(id, approve, comments[id] ?? '');
      setResult({ tone: 'good', text: approve ? 'Reply sent to the requester.' : 'Reply rejected. The agent has been told.' });
      reload();
    } catch (e) {
      setResult({ tone: 'warn', text: e instanceof ApiError ? e.message : 'That did not go through. Try again.' });
    } finally {
      inFlight.current = false;
      setDeciding(null);
    }
  }

  const n = data?.length ?? 0;
  const pending = tab === 'PENDING';
  const heading = !data
    ? 'Approvals'
    : pending
      ? (n === 0 ? 'Nothing is waiting for you.' : `${n} ${n === 1 ? 'reply needs' : 'replies need'} your decision.`)
      : `${n} ${n === 1 ? 'reply' : 'replies'} ${tab === 'APPROVED' ? 'approved' : 'rejected'}.`;

  return (
    <>
      <div className="hero">
        <h1>{heading}</h1>
        <p className="lede">{LEDE[tab]}</p>
      </div>

      <div className="tabs" aria-label="Approval status">
        {TABS.map((t) => (
          <button
            key={t.value}
            className={tab === t.value ? 'btn' : 'btn btn-line'}
            aria-pressed={tab === t.value}
            onClick={() => { setTab(t.value); setResult(null); }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <Status loading={loading} error={error} />
      {result && <Notice tone={result.tone}>{result.text}</Notice>}
      {data?.length === 0 && <Empty title={EMPTY[tab].title}>{EMPTY[tab].body}</Empty>}

      {(data ?? []).map((a) => (
        <Panel key={a.id} title={a.ticketTitle} aside={<RiskMark level={a.riskLevel} />}>
          <p className="muted" style={{ marginBottom: 12 }}>
            Ticket #{a.ticketId}, drafted {fmtDate(a.createdAt)}. <Link to={`/tickets/${a.ticketId}`}>Open the ticket</Link>
          </p>
          <div className="letter" style={{ '--edge': RISK_COLOR[a.riskLevel] } as CSSProperties}>{a.responseBody}</div>
          {a.riskFlags.length > 0 && <p className="reply-note">Held because it {a.riskFlags.map(flagText).join(', and ')}.</p>}

          {pending ? (
            <div className="row" style={{ marginTop: 18 }}>
              <div className="grow">
                <label htmlFor={`c${a.id}`}>Note for the agent <span className="hint">(optional)</span></label>
                <input id={`c${a.id}`} value={comments[a.id] ?? ''} onChange={(e) => setComments({ ...comments, [a.id]: e.target.value })} />
              </div>
              <button className="btn" disabled={deciding !== null} onClick={() => decide(a.id, true)}>
                {deciding === a.id ? 'Sending…' : 'Approve and send'}
              </button>
              <button className="btn btn-danger" disabled={deciding !== null} onClick={() => decide(a.id, false)}>
                Reject reply
              </button>
            </div>
          ) : (
            <p className="reply-note">
              {a.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {a.decidedBy ?? 'a manager'} on {fmtDate(a.decidedAt)}.
              {a.decisionComment ? ` Note: ${a.decisionComment}` : ''}
            </p>
          )}
        </Panel>
      ))}
    </>
  );
}
```

### Add to the end of `frontend/src/index.css`

```css
/* ---------- approvals status tabs ---------- */
.tabs { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 18px; }
```

### Verify

```bash
cd frontend
npm ci
npm run build          # must pass: tsc --noEmit is part of the build
npm run dev
```

Manual check as `manager@ops.local` (password in `README.md`):

1. **Approvals** → "Waiting for you" shows pending items with approve/reject.
2. Switch to **Approved** → previously approved replies show "Approved by … on …".
3. Switch to **Rejected** → rejected replies show the manager's note.
4. Approve an item → it disappears from Pending and, after a refresh, appears in Approved.

### Commit

```bash
git add frontend/src/pages/ApprovalsPage.tsx frontend/src/index.css
git commit -m "feat(ui): add approval history tabs for approved and rejected replies"
git push -u origin feat/approvals-tabs
```

---

## Task 2 — Route-level code splitting (bundle size)

**Why:** `App.tsx` imports all 11 pages eagerly, including the 3D login scene and the
chart-heavy pages. `docs/BACKLOG.md` lists this as a known improvement: the bundle is
about 600 kB minified.

**Files:** `frontend/src/App.tsx` only.

### Full replacement for `frontend/src/App.tsx`

```tsx
import { Suspense, lazy, type ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';

// Each page is its own chunk. The login page also pulls in the 3D workflow scene,
// so loading it lazily keeps that cost off every other route.
const LoginPage = lazy(() => import('./pages/LoginPage'));
const TicketsPage = lazy(() => import('./pages/TicketsPage'));
const NewTicketPage = lazy(() => import('./pages/NewTicketPage'));
const TicketDetailPage = lazy(() => import('./pages/TicketDetailPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ForecastPage = lazy(() => import('./pages/ForecastPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'));
const CompliancePage = lazy(() => import('./pages/CompliancePage'));
const EmployeesPage = lazy(() => import('./pages/EmployeesPage'));

function RequireAuth({ children, managerOnly, adminOnly }: {
  children: ReactElement; managerOnly?: boolean; adminOnly?: boolean;
}) {
  const { user, loading, isManager, isAdmin } = useAuth();
  if (loading) return <p className="muted pad">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (managerOnly && !isManager) return <Navigate to="/tickets" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/tickets" replace />;
  return children;
}

export default function App() {
  const { user } = useAuth();
  return (
    <Suspense fallback={<p className="muted pad">Loading…</p>}>
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/tickets" replace /> : <LoginPage />} />
        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route path="/tickets" element={<TicketsPage />} />
          <Route path="/tickets/new" element={<NewTicketPage />} />
          <Route path="/tickets/:id" element={<TicketDetailPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/dashboard" element={<RequireAuth managerOnly><DashboardPage /></RequireAuth>} />
          <Route path="/forecast" element={<RequireAuth managerOnly><ForecastPage /></RequireAuth>} />
          <Route path="/reports" element={<RequireAuth managerOnly><ReportsPage /></RequireAuth>} />
          <Route path="/approvals" element={<RequireAuth managerOnly><ApprovalsPage /></RequireAuth>} />
          <Route path="/compliance" element={<RequireAuth managerOnly><CompliancePage /></RequireAuth>} />
          <Route path="/employees" element={<RequireAuth adminOnly><EmployeesPage /></RequireAuth>} />
        </Route>
        <Route path="*" element={<Navigate to="/tickets" replace />} />
      </Routes>
    </Suspense>
  );
}
```

### Verify

```bash
cd frontend
npm run build
```

Before and after the change, compare the build output. The old build printed one big
`index-*.js`; after the change you should see separate chunks such as
`LoginPage-*.js`, `DashboardPage-*.js`, `recharts-*.js`, and a much smaller entry chunk.
Then click through every route with `npm run dev` — each page should still render.

### Commit

```bash
git add frontend/src/App.tsx
git commit -m "perf(ui): code-split routes and lazy-load the login scene"
```

---

## Task 3 — Add ESLint (missing tooling)

**Why:** `docs/TEAM_GUIDE.md` says there is no frontend linting and no test runner.
Adding ESLint is a contained, useful contribution. Do this on its own branch.

```bash
git switch main && git pull --ff-only origin main
git switch -c feat/frontend-lint
cd frontend
npm install -D eslint @eslint/js typescript-eslint globals eslint-plugin-react-hooks
```

### New file `frontend/eslint.config.js`

```js
import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', '.vite', 'node_modules'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { ecmaVersion: 2022, globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      // The classic two rules only: the newer recommended preset flags existing
      // setState-in-effect patterns that are out of scope for this task.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
);
```

`eslint-plugin-react-hooks` is needed because `useAsync.ts` already carries an
`// eslint-disable-next-line react-hooks/exhaustive-deps` directive. Without the plugin,
ESLint reports "Definition for rule … was not found".

### Add the script to `frontend/package.json`

```json
"scripts": {
  "dev": "vite",
  "build": "tsc --noEmit && vite build",
  "preview": "vite preview",
  "lint": "eslint ."
},
```

Run it:

```bash
npm run lint
```

Fix any real findings it reports (unused imports are the usual ones). Do not silence
rules globally just to get a pass.

### Commit

```bash
git add frontend/package.json frontend/package-lock.json frontend/eslint.config.js
git add -A frontend/src
git commit -m "chore(ui): add ESLint flat config and lint script"
git push -u origin feat/frontend-lint
```

Ask the backend lead to add `npm run lint` to `.github/workflows/ci.yml` after this merges
(that file belongs to them).
