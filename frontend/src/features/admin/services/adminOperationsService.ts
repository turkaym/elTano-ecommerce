import { deleteRequest, getJson, postJson } from '../../../shared/api/httpClient'

interface AdminOrderSummary {
  id: string
  reference: string
}

interface AdminOrderListResponse {
  content: AdminOrderSummary[]
}

interface AdminImportJobStatusResponse {
  id: string
  status: string
}

type AdminProductPayload = Record<string, unknown>

export async function listAdminProducts() {
  return getJson<Array<Record<string, unknown>>>('/api/admin/products')
}

export async function createAdminProduct(payload: AdminProductPayload) {
  return postJson<AdminProductPayload, Record<string, unknown>>('/api/admin/products', payload)
}

export async function deleteAdminProduct(productId: string) {
  await deleteRequest(`/api/admin/products/${productId}`)
}

export async function restoreAdminProduct(productId: string) {
  return postJson<Record<string, never>, Record<string, unknown>>(
    `/api/admin/products/${productId}/restore`,
    {},
  )
}

export async function listAdminOrders() {
  return getJson<AdminOrderListResponse>('/api/admin/orders')
}

export async function getAdminImportJobStatus(jobId: string) {
  return getJson<AdminImportJobStatusResponse>(`/api/admin/catalog/jobs/${jobId}`)
}
