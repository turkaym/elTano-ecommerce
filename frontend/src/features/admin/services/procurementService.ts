import { ApiClientError, buildAdminWriteHeaders } from '../../../shared/api/httpClient'

const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''
const basePath = '/api/admin/procurement'

export interface SupplierDto { id: string; name: string; taxIdentity?: string | null; active: boolean }
export interface MappingDto { id: string; supplierId: string; supplierItemCode: string; description: string; targetType: 'VARIANT_UNIT' | 'BULK_GRAM'; productId?: string | null; variantId?: string | null; defaultConversion: string | number; active: boolean }
export interface PurchaseLineDto { id: string; mappingId: string; supplierItemCode: string; supplierDescription: string; targetType: 'VARIANT_UNIT' | 'BULK_GRAM'; productId?: string | null; variantId?: string | null; orderedQuantity: string | number; conversion: string | number }
export interface PurchaseDto { id: string; supplierId?: string; supplierName: string; documentType: string; documentNumber: string; purchasedAt?: string; status: 'PENDING' | 'RECEIVED' | 'CANCELLED'; progress?: string; lines?: PurchaseLineDto[] }
export interface ReceiptDraft { lines: Array<{ purchaseLineId: string; dispositions?: Array<{ type: string; quantity: string; note?: string }>; acceptedOrdered?: string }> }
export interface ReceiptResult { receiptId?: string; status?: PurchaseDto['status']; replayed?: boolean; canonicalDeltas: Array<{ targetType: string; targetId: string; delta: number }> }
export type PurchaseDraftStatus = 'DRAFT' | 'CONFIRMED' | 'DELETED'
export type PurchaseDraftLineStatus = 'MATCHED' | 'UNRESOLVED' | 'INVALID'
export type PurchaseDraftSourceType = 'XLSX' | 'MANUAL'
export type PurchaseDraftTargetType = 'VARIANT_UNIT' | 'BULK_GRAM'
export interface PurchaseDraftLine { id: string; rowNumber: number | null; sourceDate: string | null; productName: string; sourceQuantity: string; quantity: string | number | null; unit: 'KG' | 'UNIDAD' | null; errors: string[]; matchStatus: PurchaseDraftLineStatus; targetType: PurchaseDraftTargetType | null; productId: string | null; variantId: string | null; conversion: string | number | null }
export interface PurchaseDraft { id: string; version: number; status: PurchaseDraftStatus; supplierId: string; supplierName: string; purchaseDate: string; sourceType: PurchaseDraftSourceType; originalFilename: string | null; sourceSha256: string | null; previewHash: string | null; confirmedPurchaseId: string | null; reused: boolean; lines: PurchaseDraftLine[] }
export interface PurchaseDraftLineCommand { version: number; productName: string; quantity: string; unit: string }
export interface CatalogCandidate { value: string; label: string; targetType: PurchaseDraftTargetType }
export interface CanonicalInventoryDelta { lineId: string; targetType: PurchaseDraftTargetType; targetId: string; delta: number }
export interface PurchaseDraftRowError { rowNumber: number | null; code: string; message: string }
export interface PurchaseDraftPreview { version: number; ready: boolean; previewHash: string | null; canonicalDeltas: CanonicalInventoryDelta[]; errors: PurchaseDraftRowError[] }
export interface PurchaseDraftConfirmation { draftId: string; purchaseId: string; receiptId: string; replayed: boolean; canonicalDeltas: CanonicalInventoryDelta[] }

export function listSuppliers() { return request<SupplierDto[]>(`${basePath}/suppliers`) }
export function createSupplier(payload: { name: string; taxIdentity?: string }) { return request<SupplierDto>(`${basePath}/suppliers`, { method: 'POST', body: payload }) }
export function updateSupplier(id: string, payload: Partial<SupplierDto>) { return request<SupplierDto>(`${basePath}/suppliers/${id}`, { method: 'PATCH', body: payload }) }
export function listMappings(supplierId?: string) { return request<MappingDto[]>(`${basePath}/mappings${supplierId ? `?supplierId=${encodeURIComponent(supplierId)}` : ''}`) }
export function createMapping(payload: Record<string, unknown>) { return request<MappingDto>(`${basePath}/mappings`, { method: 'POST', body: payload }) }
export function updateMapping(id: string, payload: Partial<MappingDto>) { return request<MappingDto>(`${basePath}/mappings/${id}`, { method: 'PATCH', body: payload }) }

export async function listPurchases(filters: { status?: string; supplierId?: string } = {}) {
  const query = new URLSearchParams()
  if (filters.status) query.set('status', filters.status)
  if (filters.supplierId) query.set('supplierId', filters.supplierId)
  return request<PurchaseDto[]>(`${basePath}/purchases${query.size ? `?${query}` : ''}`)
}
export function getPurchase(id: string) { return request<PurchaseDto>(`${basePath}/purchases/${id}`) }
export function createPurchase(payload: Record<string, unknown>) { return request<PurchaseDto>(`${basePath}/purchases`, { method: 'POST', body: payload }) }
export function updatePurchase(id: string, payload: Record<string, unknown>) { return request<PurchaseDto>(`${basePath}/purchases/${id}`, { method: 'PUT', body: payload }) }
export function previewReceipt(id: string, draft: ReceiptDraft) { return request<ReceiptResult>(`${basePath}/purchases/${id}/receipts/preview`, { method: 'POST', body: normalizeDraft(draft) }) }
export function confirmReceipt(id: string, draft: ReceiptDraft, idempotencyKey: string) { return request<ReceiptResult>(`${basePath}/purchases/${id}/receipts/confirm`, { method: 'POST', body: normalizeDraft(draft), headers: { 'Idempotency-Key': idempotencyKey } }) }
export function correctPurchase(id: string, payload: Record<string, unknown>, key: string) { return request<ReceiptResult>(`${basePath}/purchases/${id}/corrections`, { method: 'POST', body: payload, headers: { 'Idempotency-Key': key } }) }
export function cancelPurchase(id: string, reason: string, key: string) { return request<ReceiptResult>(`${basePath}/purchases/${id}/cancel`, { method: 'POST', body: { reason }, headers: { 'Idempotency-Key': key } }) }

const draftPath = `${basePath}/purchase-drafts`

export function downloadPurchaseDraftTemplate() { return requestBlob(`${draftPath}/template`).then(({ blob }) => blob) }
export function listPurchaseDrafts() { return request<PurchaseDraft[]>(draftPath) }
export function getPurchaseDraft(id: string) { return request<PurchaseDraft>(`${draftPath}/${id}`) }
export function createManualPurchaseDraft(payload: { supplierId: string; purchaseDate: string; lines: Array<{ productName: string; quantity: string; unit: string }> }) { return request<PurchaseDraft>(draftPath, { method: 'POST', body: payload }) }
export function importPurchaseWorkbook(supplierId: string, file: File, idempotencyKey: string) {
  const body = new FormData()
  body.set('supplierId', supplierId)
  body.set('file', file)
  return request<PurchaseDraft>(`${draftPath}/imports`, { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } })
}
export function addPurchaseDraftLine(draftId: string, payload: PurchaseDraftLineCommand) { return request<PurchaseDraft>(`${draftPath}/${draftId}/lines`, { method: 'POST', body: payload }) }
export function updatePurchaseDraftLine(draftId: string, lineId: string, payload: PurchaseDraftLineCommand) { return request<PurchaseDraft>(`${draftPath}/${draftId}/lines/${lineId}`, { method: 'PATCH', body: payload }) }
export function deletePurchaseDraftLine(draftId: string, lineId: string, version: number) { return request<PurchaseDraft>(`${draftPath}/${draftId}/lines/${lineId}`, { method: 'DELETE', body: { version } }) }
export function listCatalogCandidates(query: string, unit: string, limit = 20) { return request<CatalogCandidate[]>(`${draftPath}/catalog-candidates?${new URLSearchParams({ q: query, unit, limit: String(limit) })}`) }
export function matchPurchaseDraftLine(draftId: string, lineId: string, payload: { version: number; targetId: string; remember: boolean }) { return request<PurchaseDraft>(`${draftPath}/${draftId}/lines/${lineId}/match`, { method: 'PUT', body: payload }) }
export function previewPurchaseDraft(draftId: string, version: number) { return request<PurchaseDraftPreview>(`${draftPath}/${draftId}/preview`, { method: 'POST', body: { version } }) }
export function confirmPurchaseDraft(draftId: string, payload: { version: number; previewHash: string }, idempotencyKey: string) { return request<PurchaseDraftConfirmation>(`${draftPath}/${draftId}/confirm`, { method: 'POST', body: payload, headers: { 'Idempotency-Key': idempotencyKey } }) }
export function downloadPurchaseDraftSource(draftId: string) { return requestBlob(`${draftPath}/${draftId}/source-file`) }

function normalizeDraft(draft: ReceiptDraft): ReceiptDraft {
  return { lines: draft.lines.map((line) => line.dispositions ? line : { purchaseLineId: line.purchaseLineId, dispositions: [{ type: 'ACCEPTED_ORDERED', quantity: line.acceptedOrdered ?? '0' }] }) }
}

async function request<T>(path: string, options: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const isFormData = options.body instanceof FormData
  const response = await fetch(`${API_URL}${path}`, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body === undefined || isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...buildAdminWriteHeaders(path, method, options.headers ?? {}),
    },
    body: options.body === undefined ? undefined : options.body instanceof FormData ? options.body : JSON.stringify(options.body),
  })
  if (!response.ok) {
    let payload: { message?: string; code?: string; correlationId?: string; fieldErrors?: Array<{ field: string; message: string }> } = {}
    try { payload = await response.json() as typeof payload } catch { /* preserve status fallback */ }
    throw new ApiClientError(response.status, payload.message ?? `Request failed with status ${response.status}`, payload)
  }
  return await response.json() as T
}

async function requestBlob(path: string): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch(`${API_URL}${path}`, { credentials: 'include', headers: { Accept: 'application/octet-stream' } })
  if (!response.ok) {
    let payload: { message?: string; code?: string; correlationId?: string } = {}
    try { payload = await response.json() as typeof payload } catch { /* preserve status fallback */ }
    throw new ApiClientError(response.status, payload.message ?? `Request failed with status ${response.status}`, payload)
  }
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  return { blob: await response.blob(), filename: encoded ? decodeURIComponent(encoded) : plain ?? 'compra.xlsx' }
}
