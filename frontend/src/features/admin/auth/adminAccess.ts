import { ApiClientError, isUnauthorizedError } from '../../../shared/api/httpClient'
import { listAdminOrders } from '../services/adminOperationsService'

export const adminSessionRoleStorageKey = 'admin-session-role'

export type AdminAccessState = 'loading' | 'authenticated' | 'unauthenticated' | 'forbidden' | 'service-unavailable'

export const adminGuardMessages = {
  unauthenticated: 'Inicia sesión para entrar al panel admin.',
  forbidden: 'Tu usuario no tiene permisos de administrador.',
  serviceUnavailable: 'No pudimos verificar el acceso admin por un error del servidor. Intentá nuevamente en unos minutos.',
  cancelUnsupported: 'Cancelación no disponible: contrato backend no soportado (501).',
} as const

export function hasAdminSession(): boolean {
  return window.sessionStorage.getItem(adminSessionRoleStorageKey) === 'admin'
}

export async function bootstrapAdminSession(): Promise<AdminAccessState> {
  try {
    await listAdminOrders({ page: 0, size: 1 })
    window.sessionStorage.setItem(adminSessionRoleStorageKey, 'admin')
    return 'authenticated'
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 401) {
      clearAdminSessionMarker()
      return 'unauthenticated'
    }
    if (error instanceof ApiClientError && error.status === 403) {
      clearAdminSessionMarker()
      return 'forbidden'
    }
    throw error
  }
}

export function clearAdminSessionMarker() {
  window.sessionStorage.removeItem(adminSessionRoleStorageKey)
}

export function isAdminUnauthorized(error: unknown): boolean {
  return isUnauthorizedError(error)
}
