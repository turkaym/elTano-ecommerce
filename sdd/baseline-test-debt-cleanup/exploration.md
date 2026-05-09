## Exploration: Stabilize unrelated baseline test failures blocking full-suite green CI after admin async worker archive

### Current State
Recent backend security/audit wiring and frontend route-flag usage exposed three independent test-debt failures unrelated to checkout/payment business logic. In backend MVC slice tests, admin write requests now hit CSRF protection and return 403 when unauthenticated POSTs omit CSRF tokens. Also, MVC context loading for `MercadoPagoWebhookControllerTest` now fails because `AdminAuditInterceptor` is globally registered and requires `AdminAuditService`, but the test does not mock that dependency. In frontend Vitest, `App.payment.test.tsx` mocks `shared/config/flags` incompletely; `AppRoutes` now imports `adminDashboardEnabled`, so module mock shape mismatch crashes before assertions.

### Affected Areas
- `backend/src/test/java/com/eltano/ecommerce/orders/api/OrderDraftControllerTest.java` — assertion expects 401 for unauthenticated admin POST, but actual status is 403 under CSRF-first rejection.
- `backend/src/main/java/com/eltano/ecommerce/config/SecurityConfig.java` — admin mutating endpoints enforce CSRF (`adminWriteRequestMatcher`), driving the 403 behavior.
- `backend/src/test/java/com/eltano/ecommerce/orders/api/MercadoPagoWebhookControllerTest.java` — missing `AdminAuditService` mock causes `ApplicationContext` startup failure.
- `backend/src/main/java/com/eltano/ecommerce/audit/api/AdminAuditInterceptor.java` — constructor dependency on `AdminAuditService` now required wherever MVC context includes interceptor.
- `backend/src/main/java/com/eltano/ecommerce/config/WebMvcConfig.java` — global interceptor registration means even non-admin controller tests must satisfy interceptor dependencies.
- `frontend/src/app/App.payment.test.tsx` — flags mock exports only checkout flags and omits `adminDashboardEnabled`.
- `frontend/src/shared/config/flags.ts` and `frontend/src/app/routes/AppRoutes.tsx` — current runtime contract requires `adminDashboardEnabled` export.

### Approaches
1. **Test-aligned minimal remediation** — update tests/mocks to match current runtime contracts.
   - Pros: Lowest risk; no production behavior changes; fastest to restore green CI.
   - Cons: Preserves current architectural coupling in MVC tests (global interceptor dependency).
   - Effort: Low.

2. **Behavior rollback for compatibility** — alter security/interceptor wiring to recover old test assumptions.
   - Pros: Could restore 401 expectation and reduce test fixtures in some suites.
   - Cons: Higher regression risk (security semantics and audit coverage), may weaken intended protections introduced by admin async worker/audit work.
   - Effort: Medium.

3. **Structural decoupling** — make admin audit interceptor conditional/path-scoped bean or test-profile gated.
   - Pros: Reduces incidental coupling for non-admin MVC slices long-term.
   - Cons: More design and verification work; risk of accidentally disabling auditing in real runtime.
   - Effort: Medium/High.

### Recommendation
Choose **Approach 1 (test-aligned minimal remediation)** for this cleanup change. It directly addresses baseline test debt with the safest blast radius: (a) update `OrderDraftControllerTest` assertion to expect 403 for unauthenticated admin POST without CSRF, (b) add `@MockBean AdminAuditService` to `MercadoPagoWebhookControllerTest` (matching other MVC tests already adapted), and (c) fix `App.payment.test.tsx` flags mock to include `adminDashboardEnabled` (or use partial mock via `importOriginal` to preserve exports). This path restores CI without altering production security/audit logic.

### Risks
- Risk of masking security intent if status-code expectation is changed without documenting CSRF-vs-auth precedence.
- Risk of future mock drift in frontend if flags module gains additional exports and tests keep full-module hard mocks.
- Residual architectural coupling risk: global interceptor dependencies may continue to break new `@WebMvcTest` classes unless test template includes `AdminAuditService` mock.

### Ready for Proposal
Yes — proceed to `sdd-propose` with a narrow “test-debt cleanup only” scope and explicit non-goal of changing runtime security/audit behavior.
