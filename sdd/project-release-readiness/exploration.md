## Exploration: Final hardening to finish project: stabilize admin coverage flake, run final done-checklist, and confirm ready-to-release state.

### Current State
Admin dashboard work is archived with PASS WITH WARNINGS, and the main open warning is a coverage-only flaky timeout in `AppRoutes.admin-routes.test.tsx`. The test currently depends on `globalThis.fetch` and waits directly for `Sin productos`; under coverage instrumentation it can stay in loading (`Cargando productos…`) long enough to timeout. Project-level verification contract already exists in `README.md` (backend verify + frontend ci/test/lint/build), but there is no explicit final "release gate" checklist that combines backend/frontend quality, flaky-test stability criteria, and repository governance rules.

### Affected Areas
- `frontend/src/app/routes/AppRoutes.admin-routes.test.tsx` — flaky test under coverage mode; current mock/wait strategy is timing-sensitive.
- `frontend/src/features/admin/auth/AdminGuard.tsx` — async guard bootstrap controls when nested admin routes render.
- `frontend/src/features/admin/pages/AdminProductsPage.tsx` — loading/empty-state transition being asserted by the flaky test.
- `frontend/src/features/admin/services/adminOperationsService.ts` — network dependency used by admin page and guard bootstrap path.
- `frontend/src/test/setupTests.ts` — global test hygiene point; currently only `cleanup()`, no automatic mock restoration.
- `README.md` — source of canonical verification contract that should be included in release-readiness gates.
- `sdd/project-final-stabilization/verify-report` — prior stabilization evidence and existing warnings to incorporate into final done-checklist.
- `sdd/admin-dashboard-final-polish/archive-report` — records known flake warning and follow-up intent; useful as release blocker/non-blocker decision input.

### Approaches
1. **Deterministic admin-route test hardening (minimal change)** — remove dependency on ambient `fetch` timing and assert route behavior through controlled module-level mocks + explicit async boundaries.
   - Pros: Smallest scope; directly targets flaky test; low regression risk; fast to validate in repeated coverage runs.
   - Cons: Needs careful mock boundary design to avoid over-mocking integration behavior.
   - Effort: Low

2. **Broader test-runtime hardening** — add global mock-reset policy and rework multiple admin tests for stronger isolation.
   - Pros: Improves overall test determinism beyond one flaky file.
   - Cons: Wider blast radius; higher chance of touching unrelated tests; more verification time.
   - Effort: Medium

### Recommendation
Use **Approach 1** now for release readiness: patch only `AppRoutes.admin-routes.test.tsx` to be deterministic (mock admin auth + admin product service explicitly, add mock restoration in test lifecycle, and wait for loading-to-settled transition before final assertion). Then execute a **final done-checklist** with strict release gates. Keep Approach 2 as post-release hardening if additional flakes appear.

Suggested minimal fix strategy for the flake:
- Stop using bare `vi.spyOn(globalThis, 'fetch').mockResolvedValue(...)` in this route test.
- Mock the exact async dependency being asserted (`listAdminProducts`) and the guard bootstrap (`bootstrapAdminSession`) with deterministic resolved values.
- Add `afterEach(() => vi.restoreAllMocks())` in the test file (or global setup if chosen later).
- Assert transitional behavior explicitly (optional but stabilizing): first verify loading state appears, then await its disappearance/settled UI and assert `Sin productos`.
- Validate with repeated coverage command for this file and then full `npx vitest run --coverage`.

### Risks
- Over-mocking can hide real integration regressions if route tests stop exercising expected HTTP wiring.
- Coverage runs can still expose unrelated timing-sensitive tests; release gate must require repeatability, not a single green run.
- If checklist gates are not explicit, teams may ship with unresolved warning debt interpreted inconsistently.

### Ready for Proposal
Yes — proceed to `sdd-propose` with scope limited to: (1) deterministic fix for admin coverage flake, and (2) explicit project done-checklist/release-readiness gates.

Proposed final project-done checklist / release-readiness gates for the next phase:
1. **Flake gate (blocking)**: `AppRoutes.admin-routes.test.tsx` passes in coverage mode across repeated runs (e.g., 3 consecutive local runs) with no timeout/loading-state stalls.
2. **Frontend quality gate (blocking)**: `npm ci`, `npm test -- --watch=false`, `npm run lint`, `npm run build`, and `npx vitest run --coverage` all exit `0`.
3. **Backend quality gate (blocking)**: `mvn -B clean verify` exits `0` with expected test pass metrics.
4. **Canonical sequence gate (blocking)**: README’s ordered backend→frontend contract executes end-to-end with all stage exit codes `0`.
5. **Warning debt gate (blocking for release, non-blocking for future enhancements)**: no open CRITICAL issues; all WARNING items from latest verify/archive either resolved or explicitly accepted with owner + follow-up issue.
6. **Governance gate (blocking)**: release PR links an approved issue, includes exactly one `type:*` label, and is opened only after issue has `status:approved`.
