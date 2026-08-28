import { ApiClientError } from '../../../shared/api/httpClient'

const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''

export interface AdminSession {
  authenticated: true
  username: string
  roles: string[]
}

function joinUrl(path: string): string {
  if (!API_URL) return path
  return `${API_URL.endsWith('/') ? API_URL.slice(0, -1) : API_URL}${path}`
}

function readCsrfToken(): string | null {
  const cookie = document.cookie.split(';').map((part) => part.trim()).find((part) => part.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null
}

async function throwResponseError(response: Response): Promise<never> {
  let body: { code?: string; message?: string } = {}
  try {
    body = await response.json() as typeof body
  } catch {
    // Keep a stable fallback for non-JSON infrastructure errors.
  }
  throw new ApiClientError(response.status, body.message ?? `Request failed with status ${response.status}`, { code: body.code })
}

export async function bootstrapAdminCsrf(): Promise<void> {
  const response = await fetch(joinUrl('/api/admin/auth/csrf'), { credentials: 'include', headers: { Accept: 'application/json' } })
  if (!response.ok) await throwResponseError(response)
}

export async function loginAdmin(username: string, password: string): Promise<void> {
  await bootstrapAdminCsrf()
  const csrfToken = readCsrfToken()
  const response = await fetch(joinUrl('/api/admin/auth/login'), {
    method: 'POST',
    credentials: 'include',
    headers: { Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: new URLSearchParams({ username, password }),
  })
  if (!response.ok) await throwResponseError(response)
}

export async function getAdminSession(): Promise<AdminSession> {
  const response = await fetch(joinUrl('/api/admin/auth/session'), { credentials: 'include', headers: { Accept: 'application/json' } })
  if (!response.ok) await throwResponseError(response)
  return await response.json() as AdminSession
}

export async function logoutAdmin(): Promise<void> {
  if (!readCsrfToken()) await bootstrapAdminCsrf()
  const csrfToken = readCsrfToken()
  const response = await fetch(joinUrl('/api/admin/auth/logout'), {
    method: 'POST',
    credentials: 'include',
    headers: { Accept: 'application/json', ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
  })
  if (!response.ok && response.status !== 401) await throwResponseError(response)
}
