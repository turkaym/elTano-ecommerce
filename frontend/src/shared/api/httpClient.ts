const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''
const ADMIN_BASIC_USER = import.meta.env.VITE_ADMIN_BASIC_USER?.trim() ?? ''
const ADMIN_BASIC_PASS = import.meta.env.VITE_ADMIN_BASIC_PASS?.trim() ?? ''

interface ApiErrorPayload {
  code?: string
  correlationId?: string
  fieldErrors?: ApiFieldError[]
  message?: string
}

export interface ApiFieldError {
  field: string
  message: string
}

interface ApiClientErrorMetadata {
  code?: string
  correlationId?: string
  fieldErrors?: ApiFieldError[]
}

export class ApiClientError extends Error {
  status: number
  code?: string
  correlationId?: string
  fieldErrors: ApiFieldError[]

  constructor(status: number, message: string, metadata: ApiClientErrorMetadata = {}) {
    super(message)
    this.status = status
    this.code = metadata.code
    this.correlationId = metadata.correlationId
    this.fieldErrors = metadata.fieldErrors ?? []
  }
}

export function isUnauthorizedStatus(status: number): boolean {
  return status === 401 || status === 403
}

export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof ApiClientError && isUnauthorizedStatus(error.status)
}

function joinUrl(base: string, path: string): string {
  if (!base) {
    return path
  }

  const normalizedBase = base.endsWith('/') ? base.slice(0, -1) : base
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}

function buildAuthHeader(path: string): Record<string, string> {
  if (!path.startsWith('/api/admin/')) {
    return {}
  }

  if (!ADMIN_BASIC_USER || !ADMIN_BASIC_PASS) {
    return {}
  }

  return {
    Authorization: `Basic ${btoa(`${ADMIN_BASIC_USER}:${ADMIN_BASIC_PASS}`)}`,
  }
}

function buildCredentials(path: string): RequestCredentials {
  if (path.startsWith('/api/admin/')) {
    return 'include'
  }

  return 'same-origin'
}

function readCookieValue(name: string): string | null {
  if (typeof document === 'undefined' || !document.cookie) {
    return null
  }
  const cookie = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`))
  if (!cookie) {
    return null
  }
  return decodeURIComponent(cookie.slice(name.length + 1))
}

function buildCsrfHeader(path: string, method: string): Record<string, string> {
  if (!path.startsWith('/api/admin/')) {
    return {}
  }
  if (method.toUpperCase() === 'GET') {
    return {}
  }
  const token = readCookieValue('XSRF-TOKEN')
  if (!token) {
    return {}
  }
  return {
    'X-XSRF-TOKEN': token,
  }
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(joinUrl(API_URL, path), {
    credentials: buildCredentials(path),
    headers: {
      Accept: 'application/json',
      ...buildAuthHeader(path),
    },
  })

  if (!response.ok) {
    throw await buildApiClientError(response)
  }

  return (await response.json()) as T
}

export async function postJson<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse> {
  const response = await fetch(joinUrl(API_URL, path), {
    method: 'POST',
    credentials: buildCredentials(path),
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...buildAuthHeader(path),
      ...buildCsrfHeader(path, 'POST'),
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw await buildApiClientError(response)
  }

  return (await response.json()) as TResponse
}

export async function deleteRequest(path: string): Promise<void> {
  const response = await fetch(joinUrl(API_URL, path), {
    method: 'DELETE',
    credentials: buildCredentials(path),
    headers: {
      Accept: 'application/json',
      ...buildAuthHeader(path),
      ...buildCsrfHeader(path, 'DELETE'),
    },
  })

  if (!response.ok) {
    throw await buildApiClientError(response)
  }
}

export function buildAdminWriteHeaders(path: string, method: string, headers: Record<string, string>): Record<string, string> {
  return {
    ...headers,
    ...buildAuthHeader(path),
    ...buildCsrfHeader(path, method),
  }
}

async function buildApiClientError(response: Response): Promise<ApiClientError> {
  let errorMessage = `Request failed with status ${response.status}`
  let code: string | undefined
  let correlationId: string | undefined
  let fieldErrors: ApiFieldError[] = []

  try {
    const errorPayload = (await response.json()) as ApiErrorPayload
    if (errorPayload.message) {
      errorMessage = errorPayload.message
    }
    code = errorPayload.code
    correlationId = errorPayload.correlationId
    if (Array.isArray(errorPayload.fieldErrors)) {
      fieldErrors = errorPayload.fieldErrors
    }
  } catch {
    // ignore non-json errors and keep fallback message
  }

  return new ApiClientError(response.status, errorMessage, {
    code,
    correlationId,
    fieldErrors,
  })
}
