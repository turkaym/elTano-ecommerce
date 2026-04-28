import { isUnauthorizedError } from '../../../shared/api/httpClient'

export const adminSessionRoleStorageKey = 'admin-session-role'

export function hasAdminSession(): boolean {
  return window.sessionStorage.getItem(adminSessionRoleStorageKey) === 'admin'
}

export function isAdminUnauthorized(error: unknown): boolean {
  return isUnauthorizedError(error)
}
