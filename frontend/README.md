# Frontend (React + TypeScript + Vite)

## Run
```bash
cd frontend
npm install
npm run dev          # http://localhost:5173  (backend must be running on :8080)
npm run build        # type-check + production build into dist/
```
Dev server proxies `/api/*` to `http://localhost:8080` (see `vite.config.ts`), so no CORS setup is needed.
For a deployed build, serve `dist/` behind the same host as the API (or a reverse proxy that forwards `/api`).

## Structure
```
src/api/types.ts       TypeScript types mirroring docs/API.md  (update when a DTO changes)
src/api/client.ts      fetch wrapper: JWT header, error mapping, 401 -> sign out, PDF download helper
src/api/endpoints.ts   one function per backend endpoint
src/auth/              AuthContext (login/register/logout, role flags)
src/components/        Layout (sidebar + notification count), ui helpers, useAsync data hook
src/pages/             one page per feature
```

## Pages
| Route | Who | Sprint |
|-------|-----|--------|
| `/login` | everyone | – |
| `/tickets`, `/tickets/new` | everyone | 1 |
| `/tickets/:id` (replies, tone picker, risk badges, override, assign) | everyone / staff actions | 1, 2, 6 |
| `/dashboard` | manager/admin | 3 |
| `/reports` (preview + PDF) | manager/admin | 4 |
| `/forecast` | manager/admin | 5 |
| `/compliance` (risk report + audit log) | manager/admin | 6 |
| `/approvals`, `/notifications` | manager/admin, everyone | 7 |

Demo logins (password `Passw0rd!`): `employee@ops.local`, `agent.it@ops.local`, `manager@ops.local`.

## Design system
Tokens live at the top of `src/index.css`; department colours, plain-language labels and risk-flag wording live in `src/theme.ts`.
* **Colour carries meaning:** one colour per team (HR plum, IT cobalt, Finance moss, Operations saffron) is used on queue edges, charts and tags. Pine is for actions.
* **Type:** Bricolage Grotesque for headings and UI, Source Serif 4 for anything a person wrote or reads at length (descriptions, replies, report text). Fonts come from `@fontsource-variable/*`, so they work offline.
* **Voice:** plain, sentence case, active verbs. The same action keeps one name through a flow ("Draft reply" -> "Reply drafted."). Errors say what happened and what to do.
* Each page opens with a sentence built from live data instead of a generic title.

## Notes for the frontend teammate
* The token is kept in `localStorage` (simple for a bootcamp; for production prefer an httpOnly cookie).
* Route guards in `App.tsx` only improve UX; the backend enforces permissions.
* Nice next steps: ticket search, toast notifications, loading skeletons, tests (Vitest + Testing Library),
  generate types from `/v3/api-docs` with `openapi-typescript`.
