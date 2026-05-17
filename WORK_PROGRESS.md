# Edgar4j Deployment Readiness Log

**Last Updated:** May 17, 2026
**Status:** Ready for staging smoke validation

## Current State

- `main` is merged and synced with `origin/main`.
- Backend Maven tests passed before the latest readiness work.
- Frontend production build and Vitest suite passed before the latest readiness work.
- Dividend dashboard overview, history, alerts, and events now persist into a durable analysis snapshot in both Mongo-backed and file-backed resource modes.
- Dividend analysis can be reconciled against live SEC sources through `POST /api/dividend/{tickerOrCik}/reconcile`, and persisted snapshots can be retrieved through `GET /api/dividend/{tickerOrCik}/snapshot`.
- Docker Compose now exposes explicit `high` and `low` deployment profiles:
  - `docker compose --profile high up --build`
  - `docker compose --profile low up --build`
- Compose configuration validates for both profiles with `docker compose config`.
- Browser-facing frontend containers proxy `/api` and public actuator health endpoints to the backend service.
- A reusable PowerShell smoke test is available at `scripts/smoke-test.ps1`.

## Smoke Test

Run after containers are up:

```powershell
.\scripts\smoke-test.ps1 `
  -BackendBaseUrl http://localhost:8080 `
  -FrontendBaseUrl http://localhost:3000
```

For authenticated staging:

```powershell
.\scripts\smoke-test.ps1 `
  -BackendBaseUrl https://api.example.com `
  -FrontendBaseUrl https://app.example.com `
  -Username $env:EDGAR4J_SECURITY_USERNAME `
  -Password $env:EDGAR4J_SECURITY_PASSWORD
```

## Deployment Checklist

- [x] Backend image definition exists.
- [x] Frontend image definition exists.
- [x] Compose high-resource profile includes backend, frontend, MongoDB, PostgreSQL, and Redis.
- [x] Compose low-resource profile includes backend and frontend without external service dependencies.
- [x] Compose high-resource profile validates.
- [x] Compose low-resource profile validates.
- [x] Backend liveness/readiness endpoints are public through security config.
- [x] Frontend nginx exposes `/health`.
- [x] Smoke-test script checks backend probes, frontend health, frontend shell, and dividend API reachability.
- [x] Dividend dashboard analysis surfaces persist a latest snapshot.
- [x] Dividend dashboard supports explicit live-source reconciliation.
- [ ] Run smoke test against the target staging infrastructure.
- [ ] Confirm production `SEC_USER_AGENT` uses a monitored contact, not the default placeholder.
- [ ] Confirm production secrets are supplied through the hosting platform, not committed config.
- [ ] Confirm logs and rollback procedure in the hosting platform.

## Notes

The previous Docker work log described an old MongoDB container connectivity problem. The compose file has since been replaced with explicit profile-specific services and validated configuration. Final go-live approval still requires running the smoke test against the actual staging or production target because local tests cannot verify hosted secrets, DNS, TLS, ingress, or provider quotas.
