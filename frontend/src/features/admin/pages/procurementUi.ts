import { ApiClientError } from '../../../shared/api/httpClient'

export function procurementErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.status === 401) return 'Tu sesión venció. Iniciá sesión nuevamente para continuar.'
    if (error.status === 403 && error.code === 'CSRF_FORBIDDEN') return 'No pudimos validar la seguridad de tu sesión. Recargá la página e intentá nuevamente.'
    if (error.status === 403) return 'No tenés permisos para realizar esta operación.'
    return error.message
  }
  return 'No pudimos completar la operación. Intentá nuevamente.'
}

export function saveBlob(blob: Blob, filename: string) {
  if (typeof URL.createObjectURL !== 'function') return
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export function createOperationKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
