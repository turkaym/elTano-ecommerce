import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

vi.mock('../catalog/services/catalogService', () => ({
  getFeaturedProducts: vi.fn(),
}))

vi.mock('../catalog/services/catalogQueryService', () => ({
  getCatalogListItems: vi.fn(),
}))

vi.mock('../checkout/services/checkoutService', () => ({
  createOrderDraft: vi.fn(),
}))

import { getFeaturedProducts } from '../catalog/services/catalogService'
import { getCatalogListItems } from '../catalog/services/catalogQueryService'
import { createOrderDraft } from '../checkout/services/checkoutService'

function renderAppAt(pathname = '/') {
  window.history.replaceState({}, '', pathname)
  return render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  )
}

describe('Storefront non-regression smoke', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()

    vi.mocked(getFeaturedProducts).mockResolvedValue({
      source: 'api',
      products: [
        {
          id: 'prod-1',
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
          variantId: '11111111-1111-4111-8111-111111111111',
          unitLabel: 'bolsa 500 g',
          price: 6400,
          stockAvailable: 10,
        },
      ],
    })

    vi.mocked(getCatalogListItems).mockResolvedValue({
      source: 'api',
      items: [
        {
          id: 'prod-1',
          name: 'Almendra natural premium',
          description: 'Ideal para snack.',
          categoryName: 'Frutos secos',
          categorySlug: 'frutos-secos',
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
          variantId: '11111111-1111-4111-8111-111111111111',
          unitLabel: 'bolsa 500 g',
          price: 6400,
          stockAvailable: 10,
        },
      ],
    })

    vi.mocked(createOrderDraft).mockResolvedValue({
      draftId: 'draft-1',
      reference: 'ET-2026-0001',
      currency: 'ARS',
      subtotal: 6400,
      total: 6400,
      whatsappMessage: 'Hola, confirmo pedido ET-2026-0001',
    })

    vi.spyOn(window, 'open').mockImplementation(() => ({ closed: false }) as Window)
  })

  it('keeps browse shell and catalog visibility intact', async () => {
    renderAppAt('/')

    expect(screen.getByRole('navigation', { name: 'Categorías' })).toBeInTheDocument()
    expect(screen.queryByRole('search')).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Productos naturales El Tano' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Por que nos eligen' })).not.toBeInTheDocument()
    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver carrito, 0 items' })).toHaveAttribute('href', '/carrito')
    expect(screen.queryByRole('heading', { name: 'Carrito' })).not.toBeInTheDocument()
  })

  it('preserves checkout-by-whatsapp baseline flow', async () => {
    const user = userEvent.setup()
    renderAppAt('/')

    await screen.findByText('Almendra natural premium')
    await user.selectOptions(
      screen.getByLabelText('Presentacion para Almendra natural premium'),
      '11111111-1111-4111-8111-111111111111',
    )
    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(createOrderDraft).toHaveBeenCalledTimes(1)
    })

    expect(window.open).toHaveBeenCalledWith(
      'https://wa.me/5492966659577?text=Hola%2C%20confirmo%20pedido%20ET-2026-0001',
      '_blank',
      'noopener,noreferrer',
    )
  })
})
