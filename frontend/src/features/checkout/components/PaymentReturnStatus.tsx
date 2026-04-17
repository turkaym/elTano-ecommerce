import { useEffect, useMemo, useState } from 'react'
import { getDraftPaymentStatus } from '../services/checkoutService'
import type { DraftPaymentStatusResponse } from '../../../shared/types/checkout'

interface PaymentReturnStatusProps {
  draftId: string
  providerStatusHint?: string | null
  onPaidContinue: () => void
  onRetry?: () => void
}

const TERMINAL_STATUSES = new Set(['PAID', 'FAILED', 'CANCELLED', 'EXPIRED'])

export function PaymentReturnStatus({
  draftId,
  providerStatusHint,
  onPaidContinue,
  onRetry,
}: PaymentReturnStatusProps) {
  const [statusPayload, setStatusPayload] = useState<DraftPaymentStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    async function poll() {
      try {
        const payload = await getDraftPaymentStatus(draftId)
        if (cancelled) {
          return
        }

        setStatusPayload(payload)
        setError(null)

        if (!TERMINAL_STATUSES.has(payload.status)) {
          timer = window.setTimeout(() => {
            void poll()
          }, 2000)
        }
      } catch {
        if (!cancelled) {
          setError('No pudimos verificar el estado del pago. Intenta nuevamente en unos segundos.')
          timer = window.setTimeout(() => {
            void poll()
          }, 3000)
        }
      }
    }

    void poll()

    return () => {
      cancelled = true
      if (timer) {
        window.clearTimeout(timer)
      }
    }
  }, [draftId])

  const headline = useMemo(() => {
    if (!statusPayload) {
      return 'Estamos confirmando tu pago...'
    }

    switch (statusPayload.status) {
      case 'PAID':
        return 'Pago aprobado'
      case 'FAILED':
        return 'Pago rechazado'
      case 'CANCELLED':
        return 'Pago cancelado'
      case 'EXPIRED':
        return 'Pago expirado'
      default:
        return 'Estamos confirmando tu pago...'
    }
  }, [statusPayload])

  return (
    <section className="section" aria-labelledby="payment-return-title">
      <h2 id="payment-return-title">{headline}</h2>
      <p>
        Estado informado por Mercado Pago: {providerStatusHint || 'sin dato'}. Validamos siempre el estado canonico
        en backend.
      </p>

      {error ? <p role="alert">{error}</p> : null}
      {statusPayload?.status === 'PAYMENT_PENDING' || !statusPayload ? (
        <p>Estamos confirmando tu pago...</p>
      ) : null}

      {statusPayload?.status === 'PAID' ? (
        <button className="btn btn-primary" type="button" onClick={onPaidContinue}>
          Confirmar por WhatsApp
        </button>
      ) : null}

      {statusPayload && statusPayload.status !== 'PAID' && statusPayload.canRetry ? (
        <button className="btn" type="button" onClick={onRetry}>
          Reintentar pago
        </button>
      ) : null}
    </section>
  )
}
