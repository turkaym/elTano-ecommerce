## Exploration: Stabilize backend/frontend full-suite and build to reach project-finish readiness

### Current State
Current local verification is green: backend `mvn test` and `mvn verify` pass (115 tests), frontend `npm test`, `npm run build`, and `npm run lint` pass. There are no hard failures right now; the stabilization focus is reducing near-term CI risk and false-alarm signals.

### Affected Areas
- `backend/pom.xml` — defines backend verification scope (currently tests + package via Maven default lifecycle).
- `frontend/package.json` — defines frontend quality gates (`test`, `build`, `lint`) that already pass locally.
- `backend/src/test/**` (notably payment webhook repository/integration tests) — produces expected duplicate-key SQL logs that can look like failures in CI logs even when assertions pass.
- `README.md` — local run docs are present but no single “full-suite” command contract is documented at repo root.

### Approaches
1. **Baseline lock-in (minimal changes, no behavior changes)** — formalize and document the exact green verification baseline used in this exploration.
   - Pros: Lowest risk; no product code impact; fast to implement; creates a stable handoff for proposal/apply phases.
   - Cons: Does not proactively reduce flaky/noisy-test perception unless follow-up work is added.
   - Effort: Low

2. **Baseline + targeted test-noise hardening** — keep baseline commands, plus clean up expected-error test logging and standardize a single full-suite command entrypoint.
   - Pros: Better CI signal quality; easier triage when real regressions occur; still low blast radius (tests/config only).
   - Cons: Slightly more change surface than pure documentation; requires careful edits to avoid muting legitimate failures.
   - Effort: Low/Medium

### Recommendation
Proceed with **Approach 2** using strict minimal-risk sequencing: (a) codify full-suite command contract, (b) tame known expected-error test log noise, (c) keep all assertions and production logic unchanged. This improves finish-readiness without introducing domain-side risk.

### Risks
- Hidden environment drift between local and CI (Java/Node/toolchain versions) could still create non-reproducible failures.
- Over-suppressing backend test logs could hide unexpected persistence issues if done too broadly.

### Ready for Proposal
Yes — proposal should specify a stabilization-only scope (backend/frontend/config/tests), preserve current behavior, and define success as reproducible green verification from a single documented full-suite flow.
