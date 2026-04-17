import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeaturedProduct } from '../shared/types/catalog'
import { App } from './App'

vi.mock('../features/catalog/services/catalogService', () => ({
  getFeaturedProducts: vi.fn(),
}))

vi.mock('../features/checkout/services/checkoutService', () => ({
  createOrderDraft: vi.fn(),
  startDraftPayment: vi.fn(),
  getDraftPaymentStatus: vi.fn(),
}))

vi.mock('../shared/config/flags', () => ({
  checkoutMvpEnabled: true,
  checkoutPaymentEnabled: true,
}))

import { getFeaturedProducts } from '../features/catalog/services/catalogService'
import {
  createOrderDraft,
  getDraftPaymentStatus,
  startDraftPayment,
} from '../features/checkout/services/checkoutService'

const featuredProduct: FeaturedProduct = {
  id: 'prod-1',
  variantId: '11111111-1111-4111-8111-111111111111',
  name: 'Almendra natural premium',
  description: 'Ideal para snack.',
  categoryName: 'Frutos secos',
  unitLabel: 'bolsa 500 g',
  price: 6400,
  stockAvailable: 10,
}

describe('App Mercado Pago flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    vi.mocked(getFeaturedProducts).mockResolvedValue({ products: [featuredProduct], source: 'api' })
    vi.mocked(createOrderDraft).mockResolvedValue({
      draftId: 'draft-1',
      reference: 'ET-2026-0001',
      currency: 'ARS',
      subtotal: 6400,
      total: 6400,
      whatsappMessage: 'No usar en pago online',
    })
    vi.mocked(startDraftPayment).mockResolvedValue({
      draftId: 'draft-1',
      preferenceId: 'pref-1',
      initPoint: 'https://mp.test/checkout?pref_id=pref-1',
    })
    vi.mocked(getDraftPaymentStatus).mockResolvedValue({
      draftId: 'draft-1',
      reference: 'ET-2026-0001',
      status: 'PAID',
      updatedAt: '2026-04-15T00:00:05Z',
      canRetry: false,
    })

    vi.spyOn(window, 'open').mockImplementation(() => ({ closed: false }) as Window)
    window.history.replaceState({}, '', '/')
  })

  it('creates draft and redirects to Mercado Pago initPoint', async () => {
    const user = userEvent.setup()
    render(<App />)

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')
    await user.click(screen.getByRole('button', { name: 'Iniciar pago online' }))

    await waitFor(() => {
      expect(startDraftPayment).toHaveBeenCalledWith('draft-1')
    })

    expect(window.open).toHaveBeenCalledWith('https://mp.test/checkout?pref_id=pref-1', '_self')
  })

  it('opens WhatsApp continuation only after canonical PAID status on return flow', async () => {
    const user = userEvent.setup()
    window.sessionStorage.setItem(
      'checkout-payment-draft-messages',
      JSON.stringify({
        'draft-1': 'Hola, confirmo ET-2026-0001.',
      }),
    )
    window.history.replaceState({}, '', '/checkout/return?draftId=draft-1&status=rejected')

    render(<App />)

    expect(await screen.findByText('Pago aprobado')).toBeInTheDocument()
    expect(startDraftPayment).not.toHaveBeenCalled()
    expect(createOrderDraft).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirmar por WhatsApp' }))

    expect(window.open).toHaveBeenCalledWith(
      'https://wa.me/5491123456789?text=Hola%2C%20confirmo%20ET-2026-0001.',
      '_blank',
      'noopener,noreferrer',
    )
  })
})
