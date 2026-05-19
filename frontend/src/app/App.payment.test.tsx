import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeaturedProduct } from '../shared/types/catalog'
import { App } from './App'

vi.mock('../features/catalog/services/catalogService', () => ({
  getFeaturedProducts: vi.fn(),
}))

vi.mock('../features/catalog/services/catalogQueryService', () => ({
  getCatalogListItems: vi.fn(),
}))

vi.mock('../features/checkout/services/checkoutService', () => ({
  createOrderDraft: vi.fn(),
  startDraftPayment: vi.fn(),
  getDraftPaymentStatus: vi.fn(),
}))

vi.mock('../shared/config/flags', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../shared/config/flags')>()
  return {
    ...actual,
    checkoutMvpEnabled: true,
    checkoutPaymentEnabled: true,
    adminDashboardEnabled: false,
  }
})

import { getFeaturedProducts } from '../features/catalog/services/catalogService'
import { getCatalogListItems } from '../features/catalog/services/catalogQueryService'
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
  productType: 'ENVASADO',
  inventoryPolicy: 'PER_VARIANT',
  variants: [
    {
      id: '11111111-1111-4111-8111-111111111111',
      unitLabel: 'bolsa 500 g',
      price: 6400,
      stockAvailable: 10,
    },
  ],
  isMultiVariant: false,
  minPrice: 6400,
  unitLabel: 'bolsa 500 g',
  price: 6400,
  stockAvailable: 10,
}

function renderAppAt(pathname = '/') {
  window.history.replaceState({}, '', pathname)
  return render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  )
}

function getProductCard(productName: string) {
  const productCard = screen.getByRole('heading', { name: productName }).closest('article')
  expect(productCard).not.toBeNull()
  return productCard as HTMLElement
}

describe('App Mercado Pago flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    vi.mocked(getFeaturedProducts).mockResolvedValue({ products: [featuredProduct], source: 'api' })
    vi.mocked(getCatalogListItems).mockResolvedValue({
      items: [{ ...featuredProduct, categorySlug: 'frutos-secos' }],
      source: 'api',
    })
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
    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')
    await user.click(screen.getByRole('button', { name: 'Iniciar pago online' }))

    await waitFor(() => {
      expect(startDraftPayment).toHaveBeenCalledWith('draft-1')
    })

    expect(window.open).toHaveBeenCalledWith('https://mp.test/checkout?pref_id=pref-1', '_self')
  })

  it('keeps checkout compatibility with variant-first cart lines', async () => {
    const user = userEvent.setup()
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      source: 'api',
      items: [
        {
          ...featuredProduct,
          categorySlug: 'frutos-secos',
          variantId: null,
          unitLabel: 'Seleccionar presentación',
          isMultiVariant: true,
          variants: [
            {
              id: '11111111-1111-4111-8111-111111111111',
              unitLabel: 'bolsa 500 g',
              price: 6400,
              stockAvailable: 10,
            },
            {
              id: '22222222-2222-4222-8222-222222222222',
              unitLabel: 'bolsa 1 kg',
              price: 11800,
              stockAvailable: 7,
            },
          ],
        },
      ],
    })

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    const productCard = getProductCard('Almendra natural premium')
    await user.selectOptions(
      within(productCard).getByLabelText('Presentacion para Almendra natural premium'),
      '22222222-2222-4222-8222-222222222222',
    )
    fireEvent.change(within(productCard).getByLabelText('Cantidad para Almendra natural premium'), {
      target: { value: '2' },
    })
    await user.click(within(productCard).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 2 items' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')
    await user.click(screen.getByRole('button', { name: 'Iniciar pago online' }))

    await waitFor(() => {
      expect(createOrderDraft).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ variantId: '22222222-2222-4222-8222-222222222222', quantity: 2 }],
        }),
      )
    })
    expect(startDraftPayment).toHaveBeenCalledWith('draft-1')
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

    renderAppAt('/checkout/return?draftId=draft-1&status=rejected')

    expect(await screen.findByText('Pago aprobado')).toBeInTheDocument()
    expect(startDraftPayment).not.toHaveBeenCalled()
    expect(createOrderDraft).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirmar por WhatsApp' }))

    expect(window.open).toHaveBeenCalledWith(
      'https://wa.me/5492966659577?text=Hola%2C%20confirmo%20ET-2026-0001.',
      '_blank',
      'noopener,noreferrer',
    )
  })

  it('keeps retry path active for non-paid checkout return direct URL entries', async () => {
    const user = userEvent.setup()
    vi.mocked(getDraftPaymentStatus).mockResolvedValueOnce({
      draftId: 'draft-1',
      reference: 'ET-2026-0001',
      status: 'FAILED',
      updatedAt: '2026-04-15T00:00:05Z',
      canRetry: true,
    })

    renderAppAt('/checkout/return?draftId=draft-1&status=approved')

    expect(await screen.findByText('Pago rechazado')).toBeInTheDocument()
    expect(screen.getByText(/Estado informado por Mercado Pago: approved/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirmar por WhatsApp' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reintentar pago' }))

    await waitFor(() => {
      expect(startDraftPayment).toHaveBeenCalledWith('draft-1')
    })

    expect(window.open).toHaveBeenCalledWith('https://mp.test/checkout?pref_id=pref-1', '_self')
  })
})
