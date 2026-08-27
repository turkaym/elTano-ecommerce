import { ApiClientError, buildAdminWriteHeaders } from '../../../shared/api/httpClient'

const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''
const basePath = '/api/admin/procurement'

export interface SupplierDto { id: string; name: string; taxIdentity?: string | null; active: boolean }
export interface MappingDto { id: string; supplierId: string; supplierItemCode: string; description: string; targetType: 'VARIANT_UNIT' | 'BULK_GRAM'; productId?: string | null; variantId?: string | null; defaultConversion: string | number; active: boolean }
export interface PurchaseLineDto { id: string; mappingId: string; supplierItemCode: string; supplierDescription: string; targetType: 'VARIANT_UNIT' | 'BULK_GRAM'; productId?: string | null; variantId?: string | null; orderedQuantity: string | number; conversion: string | number }
export interface PurchaseDto { id: string; supplierId?: string; supplierName: string; documentType: string; documentNumber: string; purchasedAt?: string; status: 'PENDING' | 'RECEIVED' | 'CANCELLED'; progress?: string; lines?: PurchaseLineDto[] }
export interface ReceiptDraft { lines: Array<{ purchaseLineId: string; dispositions?: Array<{ type: string; quantity: string; note?: string }>; acceptedOrdered?: string }> }
export interface ReceiptResult { receiptId?: string; status?: PurchaseDto['status']; replayed?: boolean; canonicalDeltas: Array<{ targetType: string; targetId: string; delta: number }> }

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

function normalizeDraft(draft: ReceiptDraft): ReceiptDraft {
  return { lines: draft.lines.map((line) => line.dispositions ? line : { purchaseLineId: line.purchaseLineId, dispositions: [{ type: 'ACCEPTED_ORDERED', quantity: line.acceptedOrdered ?? '0' }] }) }
}

async function request<T>(path: string, options: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const response = await fetch(`${API_URL}${path}`, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...buildAdminWriteHeaders(path, method, options.headers ?? {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  if (!response.ok) {
    let payload: { message?: string; code?: string; correlationId?: string; fieldErrors?: Array<{ field: string; message: string }> } = {}
    try { payload = await response.json() as typeof payload } catch { /* preserve status fallback */ }
    throw new ApiClientError(response.status, payload.message ?? `Request failed with status ${response.status}`, payload)
  }
  return await response.json() as T
}
