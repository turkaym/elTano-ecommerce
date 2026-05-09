## Exploration: Identify and close remaining functional/UX/operational debt in admin dashboard to finish project

### Current State
Backend admin capabilities are substantially implemented (`/api/admin/products`, `/api/admin/categories`, `/api/admin/orders`, `/api/admin/catalog/jobs`), including async catalog job lifecycle and reporting endpoints. Frontend admin is still mostly a shell: route guard + `/admin` layout exist, but there are no admin feature pages/components wired for products, categories, orders, or job operations. Access control in frontend currently depends on a client-side `sessionStorage` flag (`admin-session-role`), while backend uses HTTP Basic + ROLE_ADMIN.

### Affected Areas
- `frontend/src/app/routes/AppRoutes.tsx` — only `/admin` shell route exists; no task-level admin routes.
- `frontend/src/features/admin/layout/AdminShell.tsx` — static heading/text only, no operational UI.
- `frontend/src/features/admin/auth/adminAccess.ts` — client-side session flag controls access and can drift from backend auth state.
- `frontend/src/features/admin/services/adminOperationsService.ts` — service layer has useful calls, but response shapes are partially typed and not fully aligned with backend payloads.
- `backend/src/main/java/com/eltano/ecommerce/catalog/jobs/api/AdminCatalogJobController.java` — `cancel` endpoint returns `501`, leaving an unfinished operator flow.
- `backend/src/main/java/com/eltano/ecommerce/config/SecurityConfig.java` — in-memory basic-auth credentials are static/dev-oriented; no documented admin runtime onboarding flow.

### Approaches
1. **UI completion on current API contract** — finish the dashboard frontend against existing backend endpoints.
   - Pros: Fastest path to “project finished”; backend already covers core operations.
   - Cons: Keeps current auth/session mismatch and may require careful UX handling around 401/403 and CSRF.
   - Effort: Medium

2. **UI completion + auth/ops hardening (must-have subset)** — ship frontend pages plus minimal auth and operational closure required for production-like admin usage.
   - Pros: Closes functional, UX, and operational debt together; reduces post-launch admin risk.
   - Cons: Slightly broader scope than pure UI build.
   - Effort: Medium/High

### Recommendation
Proceed with **Approach 2** but strictly scoped to must-have finish criteria:
1) Build admin UI workflows for products/categories/orders/import-jobs using existing endpoints.
2) Replace/augment `sessionStorage`-only admin gating with backend-auth-driven session truth (at minimum, bootstrap role state from authenticated backend response and provide explicit login/logout UX).
3) Close operational dead-ends: either implement job cancel end-to-end or remove/disable cancel affordance until supported; expose job row/report error diagnostics in UI.

### Risks
- If auth semantics remain split (frontend flag vs backend authority), admins will face false-access states and confusing redirects.
- Shipping with `cancel` as `501` but visible in UI creates an unfinished critical operator path for long-running imports.
- Service typing drift between frontend and backend payload contracts can produce runtime UI regressions despite compile-time green status.

### Ready for Proposal
Yes — propose a tightly bounded “final polish” change focused on: (a) complete admin workflows, (b) auth UX/state hardening, (c) operational closure for catalog jobs. Exclude net-new domains and keep API surface changes minimal unless required to unify auth/session flow.
