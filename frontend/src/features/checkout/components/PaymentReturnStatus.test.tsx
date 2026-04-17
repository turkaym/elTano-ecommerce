import { act, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PaymentReturnStatus } from './PaymentReturnStatus'

vi.mock('../services/checkoutService', () => ({
  getDraftPaymentStatus: vi.fn(),
}))

import { getDraftPaymentStatus } from '../services/checkoutService'

describe('PaymentReturnStatus', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('polls backend and ignores provider query params as source of truth', async () => {
    vi.mocked(getDraftPaymentStatus).mockResolvedValueOnce({
      draftId: 'draft-1',
      reference: 'ET-2026-0001',
      status: 'PAID',
      updatedAt: '2026-04-15T00:00:05Z',
      canRetry: false,
    })

    render(
      <PaymentReturnStatus
        draftId="draft-1"
        providerStatusHint="rejected"
        onPaidContinue={vi.fn()}
      />,
    )

    expect(await screen.findByText('Pago aprobado')).toBeInTheDocument()
  })

  it('shows retry action when backend returns FAILED and canRetry=true', async () => {
    vi.mocked(getDraftPaymentStatus).mockResolvedValueOnce({
      draftId: 'draft-2',
      reference: 'ET-2026-0002',
      status: 'FAILED',
      updatedAt: '2026-04-15T00:00:00Z',
      canRetry: true,
    })

    render(
      <PaymentReturnStatus
        draftId="draft-2"
        providerStatusHint="approved"
        onPaidContinue={vi.fn()}
      />,
    )

    expect(await screen.findByRole('button', { name: 'Reintentar pago' })).toBeInTheDocument()
  })

  it('reconciles pending to PAID across polls and only then shows WhatsApp continuation', async () => {
    vi.useFakeTimers()
    vi.mocked(getDraftPaymentStatus)
      .mockResolvedValueOnce({
        draftId: 'draft-3',
        reference: 'ET-2026-0003',
        status: 'PAYMENT_PENDING',
        updatedAt: '2026-04-15T00:00:00Z',
        canRetry: false,
      })
      .mockResolvedValueOnce({
        draftId: 'draft-3',
        reference: 'ET-2026-0003',
        status: 'PAID',
        updatedAt: '2026-04-15T00:00:04Z',
        canRetry: false,
      })

    render(
      <PaymentReturnStatus
        draftId="draft-3"
        providerStatusHint="pending"
        onPaidContinue={vi.fn()}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Estamos confirmando tu pago...' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirmar por WhatsApp' })).not.toBeInTheDocument()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2100)
    })
    await Promise.resolve()

    expect(screen.getByRole('button', { name: 'Confirmar por WhatsApp' })).toBeInTheDocument()
  })
})
