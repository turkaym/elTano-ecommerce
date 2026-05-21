import type { ReactNode } from 'react'
import type { AdminWriteFeedback } from './adminWriteState'

export function AdminLoadingState({ label }: { label: string }) {
  return <p className="admin-feedback admin-feedback-pending" role="status">{label}</p>
}

export function AdminErrorState({ message }: { message: string }) {
  return <p className="admin-feedback admin-feedback-error" role="alert">{message}</p>
}

export function AdminEmptyState({ title, action }: { title: string; action: ReactNode }) {
  return (
    <section className="admin-empty-card">
      <h3>{title}</h3>
      {action}
    </section>
  )
}

interface AdminWriteStateProps {
  feedback: AdminWriteFeedback
  onRetry?: () => void
  onDismiss?: () => void
}

export function AdminWriteStateBanner({ feedback, onRetry, onDismiss }: AdminWriteStateProps) {
  if (feedback.status === 'idle') return null
  if (feedback.status === 'pending') return <p className="admin-feedback admin-feedback-pending" role="status">Guardando cambios…</p>
  if (feedback.status === 'success') {
    return (
      <div className="admin-feedback admin-feedback-success" role="status">
        <p>{feedback.message ?? 'Operación completada.'}</p>
        {onDismiss ? <button className="btn btn-secondary" onClick={onDismiss}>Cerrar</button> : null}
      </div>
    )
  }

  return (
    <div className="admin-feedback admin-feedback-error" role="alert">
      <p>{feedback.message ?? 'No se pudo completar la operación.'}</p>
      {feedback.fieldErrors?.length ? (
        <ul aria-label="Errores de validación">
          {feedback.fieldErrors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.message}`}>{humanizeFieldError(fieldError.field, fieldError.message)}</li>
          ))}
        </ul>
      ) : null}
      {feedback.correlationId ? <p>Correlación: {feedback.correlationId}</p> : null}
      {onRetry ? <button className="btn btn-secondary" onClick={onRetry}>Reintentar</button> : null}
      {onDismiss ? <button className="btn btn-secondary" onClick={onDismiss}>Descartar</button> : null}
    </div>
  )
}

function humanizeFieldError(field: string, message: string): string {
  const knownLabels: Record<string, string> = {
    name: 'Nombre',
    slug: 'Slug',
    active: 'Activo',
    description: 'Descripción',
    categoryId: 'Categoría',
    variants: 'Variantes',
    images: 'Imágenes',
  }

  const label = knownLabels[field] ?? field
  return `${label}: ${message}`
}
