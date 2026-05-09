## Exploration: admin-dashboard-actions-completion

### Current State
Admin dashboard shell and routes are present, but Products/Categories pages are currently read-only list views with placeholder CTA buttons (`Crear producto`, `Crear categoría`) and no form/create/edit mutation flows. Backend already exposes create/update endpoints for both products and categories (`POST/PUT /api/admin/products`, `POST/PUT /api/admin/categories`) and CSRF enforcement is active for all non-GET `/api/admin/**` requests.

Catalog jobs contract alignment from the previous archived change is now in place (jobs list + diagnostics JSON + CSV report split), and frontend service has helpers for enqueue and polling (`createAdminImportJob`, `awaitAdminImportTerminalStatus`), but the dashboard page still only reads latest job diagnostics and shows a disabled cancel action; there is no CSV upload interaction, no visible progress lifecycle, and no operator-grade success/error feedback around writes.

Test coverage is currently skewed to service-level mocked tests (admin operations + http client) and one page test for catalog jobs. There are no page-level tests for products/categories write UX, and no happy/error-path scenarios for write-state transitions.

### Affected Areas
- `frontend/src/features/admin/pages/AdminProductsPage.tsx` — needs create/edit UI and write lifecycle states instead of list-only view.
- `frontend/src/features/admin/pages/AdminCategoriesPage.tsx` — same gap as products for create/edit flows.
- `frontend/src/features/admin/services/adminOperationsService.ts` — has product create but lacks product/category update and category create wrappers needed by pages.
- `frontend/src/features/admin/pages/AdminCatalogJobsPage.tsx` — currently diagnostics-only read path; needs CSV upload trigger, enqueue/poll/progress, and error/success surfaces.
- `frontend/src/features/admin/pages/AdminPageStates.tsx` — currently only loading/error/empty primitives; no reusable pending/success/error write feedback component model.
- `frontend/src/shared/api/httpClient.ts` — already central CSRF injection point; should remain single write transport for new dashboard mutations.
- `backend/src/main/java/com/eltano/ecommerce/catalog/api/AdminProductController.java` — backend create/update is available for frontend wiring.
- `backend/src/main/java/com/eltano/ecommerce/catalog/api/AdminCategoryController.java` — backend create/update is available for frontend wiring.
- `backend/src/main/java/com/eltano/ecommerce/catalog/jobs/api/AdminCatalogJobController.java` — import endpoint and status/report endpoints support operator UX implementation.
- `frontend/src/features/admin/services/adminOperationsService.test.tsx` and `frontend/src/features/admin/pages/AdminCatalogJobsPage.test.tsx` — primary test locations to extend for happy/error write paths.

### Approaches
1. **Inline page-local mutation state (direct enhancement)** — add simple forms and per-page `idle/pending/success/error` state with local handlers.
   - Pros: Fastest to deliver; low architectural overhead; minimal file footprint.
   - Cons: Repeats write-state logic across products/categories/jobs; higher drift risk for UX consistency and error normalization.
   - Effort: Medium

2. **Shared admin action state model + page composition (recommended)** — introduce a small reusable action-state pattern (hook/component) used by product/category forms and catalog job upload/polling.
   - Pros: Consistent operational UX across all writes; easier to test happy/error transitions once and reuse; cleaner future expansion.
   - Cons: Slightly higher upfront design/refactor cost.
   - Effort: Medium

### Recommendation
Use **Approach 2**. Implement a shared mutation-state pattern in admin UI and wire three functional flows:
1) **Products**: add create + edit form interactions calling `POST/PUT /api/admin/products`, refresh list after success, and surface API validation errors.
2) **Categories**: add create + edit form interactions calling `POST/PUT /api/admin/categories`, with the same pending/success/error contract.
3) **Catalog jobs upload**: add CSV text/file input + enqueue (`/import?format=csv`) + poll (`GET /{id}`) + diagnostics refresh (`/report/diagnostics`) and explicit states for queued/processing/completed/failed.

Keep all writes routed through `httpClient`/`buildAdminWriteHeaders` to preserve CSRF behavior, and standardize operator feedback messages (pending spinner, success confirmation, correlation-aware error output when available).

### Risks
- Backend returns `204 No Content` for some endpoints (e.g., product restore), while current frontend helper signatures assume JSON in some write paths; mixed response semantics can cause runtime parse failures if reused incorrectly.
- CSV upload UX can become noisy if polling cadence is too aggressive; needs bounded polling and timeout/error messaging.
- Form payload contract for product/category upsert may include required fields not currently represented in page DTO assumptions, causing 422 churn until UI form model is aligned.
- Without a shared state model, teams may regress into inconsistent admin write UX across pages.

### Ready for Proposal
Yes — proceed to `sdd-propose` for `admin-dashboard-actions-completion` with scope limited to: product/category create-edit flows, catalog CSV upload + progress/error UX, and unified write-state UX plus happy/error-path tests.
