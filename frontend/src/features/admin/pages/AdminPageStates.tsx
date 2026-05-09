import type { ReactNode } from 'react'
import type { AdminWriteFeedback } from './adminWriteState'

export function AdminLoadingState({ label }: { label: string }) {
  return <p role="status">{label}</p>
}

export function AdminErrorState({ message }: { message: string }) {
  return <p role="alert">{message}</p>
}

export function AdminEmptyState({ title, action }: { title: string; action: ReactNode }) {
  return (
    <section>
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
  if (feedback.status === 'pending') return <p role="status">Guardando cambios…</p>
  if (feedback.status === 'success') {
    return (
      <div role="status">
        <p>{feedback.message ?? 'Operación completada.'}</p>
        {onDismiss ? <button onClick={onDismiss}>Cerrar</button> : null}
      </div>
    )
  }

  return (
    <div role="alert">
      <p>{feedback.message ?? 'No se pudo completar la operación.'}</p>
      {feedback.fieldErrors?.length ? (
        <ul aria-label="Errores de validación">
          {feedback.fieldErrors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.message}`}>{fieldError.message}</li>
          ))}
        </ul>
      ) : null}
      {feedback.correlationId ? <p>Correlación: {feedback.correlationId}</p> : null}
      {onRetry ? <button onClick={onRetry}>Reintentar</button> : null}
      {onDismiss ? <button onClick={onDismiss}>Descartar</button> : null}
    </div>
  )
}
