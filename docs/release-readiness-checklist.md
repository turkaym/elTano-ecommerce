# Release Readiness Checklist

## Blocking quality gates (binary)

**Canonical backend command policy (wrapper-first, cross-platform):**
- Windows: `./mvnw.cmd -B clean verify`
- Unix/macOS: `./mvnw -B clean verify`
- Optional alternative: `mvn -B clean verify` only when Maven is installed in `PATH`.

| Gate | Command | Timestamp (ART) | Exit code | Evidence | Status |
|---|---|---|---:|---|---|
| Frontend coverage suite | `npx vitest run --coverage` | 2026-05-02 02:07 | 0 | terminal output (22 files / 78 tests pass) | ✅ PASS |
| Frontend unit/integration tests | `npm test -- --watch=false` | 2026-05-02 02:08 | 0 | terminal output (22 files / 78 tests pass) | ✅ PASS |
| Frontend lint | `npm run lint` | 2026-05-02 02:08 | 0 | terminal output (eslint exits clean) | ✅ PASS |
| Frontend production build | `npm run build` | 2026-05-02 02:08 | 0 | terminal output (vite build success) | ✅ PASS |
| Backend verify (required command; wrapper-first policy) | `./mvnw.cmd -B clean verify` (Windows) / `./mvnw -B clean verify` (Unix) | 2026-05-02 11:02 | 0 | terminal output (BUILD SUCCESS, 118 tests) | ✅ PASS |

## Additional execution evidence (non-blocking context)

| Command | Timestamp (ART) | Exit code | Evidence |
|---|---|---:|---|
| `mvn -B clean verify` (optional when installed) | 2026-05-02 02:09 | 1 | terminal output (`mvn` not recognized in PATH at that time) |
| `./mvnw.cmd -B clean verify` | 2026-05-02 02:09 | 0 | terminal output (BUILD SUCCESS, 118 tests) |

## Warning-debt acceptance

All unresolved `WARNING`-class findings must be listed with explicit acceptance metadata before release approval.

| Warning | Accepted by (owner) | Follow-up issue/link | Accepted at | Approval |
|---|---|---|---|---|
| _None currently recorded in this run_ | — | — | — | — |

**Rule:** if any unresolved warning exists without owner + follow-up issue, release is automatically blocked.

## Final decision

- **READY:** ☑
- **NOT READY:** ☐

**Decision rationale:** all blocking gates are green under the canonical wrapper-first backend policy, with recorded evidence for `./mvnw.cmd -B clean verify`.
