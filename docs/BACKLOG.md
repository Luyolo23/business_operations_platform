# Backlog

Items from the earlier code review that are not correctness blockers.

## Optional

- **Upgrade Spring Boot.** The project is on 3.3.5, which is out of the
  supported line. Plan an upgrade to a supported 3.4/3.5 release and re-run the
  unit and integration suites.
- **Code-split the frontend bundle.** The production bundle is roughly 600 kB
  minified. Route-level `React.lazy`/dynamic imports (especially the 3D login
  scene and charts) would improve first load time. This is a performance
  improvement, not a correctness issue.

## Done

- CI workflow (`.github/workflows/ci.yml`) runs backend unit tests and the
  frontend production build on every push/PR.
- Live Groq tests are tagged `live` and run explicitly via `make live`.
