import {
  ApiClientError,
  buildAdminWriteHeaders,
  deleteRequest,
  getJson,
  postJson,
  type ApiFieldError,
} from '../../../shared/api/httpClient'

const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''
const ADMIN_BASIC_USER = import.meta.env.VITE_ADMIN_BASIC_USER?.trim() ?? ''
const ADMIN_BASIC_PASS = import.meta.env.VITE_ADMIN_BASIC_PASS?.trim() ?? ''

function joinUrl(base: string, path: string): string {
  if (!base) {
    return path
  }

  const normalizedBase = base.endsWith('/') ? base.slice(0, -1) : base
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}

function adminAuthHeader(): Record<string, string> {
  if (!ADMIN_BASIC_USER || !ADMIN_BASIC_PASS) {
    return {}
  }

  return {
    Authorization: `Basic ${btoa(`${ADMIN_BASIC_USER}:${ADMIN_BASIC_PASS}`)}`,
  }
}

interface AdminOrderSummary {
  id: string
  reference: string
  status: string
  customer: string
  total: string
  createdAt: string
}

export interface AdminOrderListResponse {
  items: AdminOrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface AdminImportJobStatusResponse {
  id: string
  status: AdminImportJobStatus
  summary?: string | null
  lastError?: string | null
}

export type AdminImportJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface AdminCategoryDto {
  id: string
  name: string
  slug: string
  deletedAt?: string | null
}

export interface AdminProductDto {
  id: string
  name: string
  slug?: string
  categoryId?: string | null
  deletedAt?: string | null
}

export interface AdminCatalogJobListDto {
  id: string
  type: string
  status: AdminImportJobStatus
  createdAt: string
  updatedAt: string
}

export interface AdminCatalogJobRowDto {
  rowNumber: number
  outcome: string
  errorCode: string | null
  errorMessage: string | null
  payload: string
}

export interface AdminCatalogJobReportDto {
  summary: string
  failedRows: number
  rows: AdminCatalogJobRowDto[]
}

interface PollOptions {
  maxAttempts?: number
  delayMs?: number
  onProgress?: (status: AdminImportJobStatusResponse) => void
}

type AdminProductPayload = Record<string, unknown>
type AdminCategoryPayload = Record<string, unknown>

export interface AdminWriteError {
  message?: string
  code?: string
  correlationId?: string
  fieldErrors?: ApiFieldError[]
}

export async function listAdminProducts() {
  return getJson<AdminProductDto[]>('/api/admin/products')
}

export async function listAdminCategories() {
  return getJson<AdminCategoryDto[]>('/api/admin/categories')
}

export async function createAdminProduct(payload: AdminProductPayload) {
  return postJson<AdminProductPayload, Record<string, unknown>>('/api/admin/products', payload)
}

export async function updateAdminProduct(productId: string, payload: AdminProductPayload) {
  return putJson<AdminProductPayload, Record<string, unknown>>(`/api/admin/products/${productId}`, payload)
}

export async function createAdminCategory(payload: AdminCategoryPayload) {
  return postJson<AdminCategoryPayload, Record<string, unknown>>('/api/admin/categories', payload)
}

export async function updateAdminCategory(categoryId: string, payload: AdminCategoryPayload) {
  return putJson<AdminCategoryPayload, Record<string, unknown>>(`/api/admin/categories/${categoryId}`, payload)
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

export async function listAdminOrders(params: { page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const suffix = query.size > 0 ? `?${query.toString()}` : ''
  return getJson<AdminOrderListResponse>(`/api/admin/orders${suffix}`)
}

export async function listAdminCatalogJobs() {
  return getJson<AdminCatalogJobListDto[]>('/api/admin/catalog/jobs')
}

export async function getAdminCatalogJobRows(jobId: string) {
  return getJson<AdminCatalogJobRowDto[]>(`/api/admin/catalog/jobs/${jobId}/rows`)
}

export async function getAdminCatalogJobReport(jobId: string) {
  return getJson<AdminCatalogJobReportDto>(`/api/admin/catalog/jobs/${jobId}/report/diagnostics`)
}

export async function getAdminImportJobStatus(jobId: string) {
  return getJson<AdminImportJobStatusResponse>(`/api/admin/catalog/jobs/${jobId}`)
}

export async function createAdminImportJob(csvPayload: string) {
  const response = await fetch(joinUrl(API_URL, `/api/admin/catalog/jobs/import?format=csv`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'text/plain',
      ...buildAdminWriteHeaders('/api/admin/catalog/jobs/import', 'POST', adminAuthHeader()),
    },
    body: csvPayload,
  })

  if (!response.ok) {
    throw new Error(`Import enqueue failed with status ${response.status}`)
  }

  return (await response.json()) as AdminImportJobStatusResponse
}

export function mapAdminWriteError(error: unknown): AdminWriteError {
  if (error instanceof ApiClientError) {
    if (error.status >= 500) {
      return {
        message: 'Error interno del servidor. Intenta nuevamente en unos minutos.',
        code: error.code,
        correlationId: error.correlationId,
        fieldErrors: error.fieldErrors,
      }
    }

    return {
      message: error.message,
      code: error.code,
      correlationId: error.correlationId,
      fieldErrors: error.fieldErrors,
    }
  }

  return { message: 'No se pudo completar la operación.' }
}

export async function awaitAdminImportTerminalStatus(
  jobId: string,
  options: PollOptions = {},
): Promise<AdminImportJobStatusResponse> {
  const maxAttempts = options.maxAttempts ?? 20
  const delayMs = options.delayMs ?? 1000

  let lastStatus: AdminImportJobStatusResponse | null = null
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const status = await getAdminImportJobStatus(jobId)
    lastStatus = status
    options.onProgress?.(status)
    if (isTerminalStatus(status.status)) {
      return status
    }

    if (delayMs > 0) {
      await wait(delayMs)
    }
  }

  return lastStatus ?? { id: jobId, status: 'QUEUED' }
}

function isTerminalStatus(status: AdminImportJobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}

function wait(delayMs: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, delayMs)
  })
}

async function putJson<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse> {
  const response = await fetch(joinUrl(API_URL, path), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...buildAdminWriteHeaders(path, 'PUT', adminAuthHeader()),
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw await mapResponseToClientError(response)
  }

  return (await response.json()) as TResponse
}

async function mapResponseToClientError(response: Response): Promise<ApiClientError> {
  let message = `Request failed with status ${response.status}`
  let code: string | undefined
  let correlationId: string | undefined
  let fieldErrors: ApiFieldError[] = []

  try {
    const body = (await response.json()) as {
      code?: string
      correlationId?: string
      message?: string
      fieldErrors?: ApiFieldError[]
    }
    message = body.message ?? message
    code = body.code
    correlationId = body.correlationId
    if (Array.isArray(body.fieldErrors)) {
      fieldErrors = body.fieldErrors
    }
  } catch {
    // noop
  }

  return new ApiClientError(response.status, message, { code, correlationId, fieldErrors })
}
