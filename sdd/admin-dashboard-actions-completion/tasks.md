# Tasks: Admin Dashboard Actions Completion

## Batch 1 (TDD): Shared frontend primitives + write-state foundation

- [ ] 1.1 RED: Add failing tests in `frontend/src/features/admin/pages/AdminPageStates.test.tsx` for shared write-state variants (`idle/pending/success/error`) and retry/dismiss actions.
- [ ] 1.2 GREEN: Implement write-state primitives in `frontend/src/features/admin/pages/AdminPageStates.tsx` (status banner, pending indicator, action buttons) with typed state contract.
- [ ] 1.3 REFACTOR: Normalize error message extraction into `frontend/src/features/admin/services/adminOperationsService.ts` helper used by all admin writes.
- [ ] 1.4 GREEN: Add missing service wrappers in `frontend/src/features/admin/services/adminOperationsService.ts` for `createCategory`, `updateCategory`, `updateProduct` using `httpClient` + admin write headers.
- [ ] 1.5 REFACTOR: Consolidate shared write-state transition utility (single reducer/hook) under `frontend/src/features/admin/pages` and update primitives usage.

## Batch 2 (TDD): Product + category create/edit flows

- [ ] 2.1 RED: Add failing page tests in `frontend/src/features/admin/pages/AdminProductsPage.test.tsx` for create success, edit success, and API error transitions.
- [ ] 2.2 GREEN: Implement product create/edit UI + submit handlers in `frontend/src/features/admin/pages/AdminProductsPage.tsx`; refresh list and reset form on success.
- [ ] 2.3 REFACTOR: Extract product form mapping/validation helpers in `frontend/src/features/admin/pages/AdminProductsPage.tsx` (or sibling helper file) to keep handlers small.
- [ ] 2.4 RED: Add failing page tests in `frontend/src/features/admin/pages/AdminCategoriesPage.test.tsx` for create success, edit success, and validation/API error display.
- [ ] 2.5 GREEN: Implement category create/edit UI + handlers in `frontend/src/features/admin/pages/AdminCategoriesPage.tsx` with same write-state contract as products.
- [ ] 2.6 REFACTOR: Align products/categories feedback copy and shared retry behavior through `AdminPageStates` primitives.

## Batch 3 (TDD): Catalog CSV upload + polling UX

- [ ] 3.1 RED: Extend `frontend/src/features/admin/pages/AdminCatalogJobsPage.test.tsx` with failing scenarios for CSV enqueue pending/success/error and terminal polling states (`queued/processing/completed/failed`).
- [ ] 3.2 GREEN: Implement CSV input + enqueue action in `frontend/src/features/admin/pages/AdminCatalogJobsPage.tsx` using `createAdminImportJob`.
- [ ] 3.3 GREEN: Wire polling lifecycle in `AdminCatalogJobsPage.tsx` via `awaitAdminImportTerminalStatus` with bounded cadence/timeout and explicit terminal feedback.
- [ ] 3.4 GREEN: Refresh diagnostics/report views after terminal completion; preserve correlation-aware error surface when available.
- [ ] 3.5 REFACTOR: Share write/poll status rendering with `AdminPageStates` primitives to avoid custom one-off banners.

## Batch 4 (TDD): Test completion, validation commands, rollback checks

- [ ] 4.1 RED/GREEN: Expand `frontend/src/features/admin/services/adminOperationsService.test.tsx` for new product/category wrappers and normalized error extraction.
- [ ] 4.2 Run validation commands from `frontend/`: `npm run lint`, `npm run test -- --runInBand`, and `npm run build`; capture failing output and fix until clean.
- [ ] 4.3 Execute manual rollback check: disable/revert admin page mutations and confirm pages return to prior read-only behavior without backend changes.
- [ ] 4.4 Verify spec criteria traceability in this task set (product flow, category flow, CSV lifecycle, happy/error automated coverage) and log any gaps before `sdd-apply` closeout.
