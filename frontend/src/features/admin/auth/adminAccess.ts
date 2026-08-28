import { ApiClientError, isUnauthorizedError } from '../../../shared/api/httpClient'
import { getAdminSession } from './adminAuthApi'

export type AdminAccessState = 'loading' | 'authenticated' | 'unauthenticated' | 'forbidden' | 'csrf-failure' | 'service-unavailable'

export const adminGuardMessages = {
  unauthenticated: 'Inicia sesión para entrar al panel admin.',
  forbidden: 'Tu usuario no tiene permisos de administrador.',
  csrfFailure: 'No pudimos validar la protección de la sesión. Recarga la página e intenta nuevamente.',
  serviceUnavailable: 'No pudimos verificar el acceso admin por un error del servidor. Intentá nuevamente en unos minutos.',
  cancelUnsupported: 'Cancelación no disponible: contrato backend no soportado (501).',
} as const

export async function bootstrapAdminSession(): Promise<AdminAccessState> {
  try {
    await getAdminSession()
    return 'authenticated'
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 401) return 'unauthenticated'
    if (error instanceof ApiClientError && error.status === 403) {
      return error.code === 'CSRF_FORBIDDEN' ? 'csrf-failure' : 'forbidden'
    }
    throw error
  }
}

export function isAdminUnauthorized(error: unknown): boolean {
  return isUnauthorizedError(error)
}
