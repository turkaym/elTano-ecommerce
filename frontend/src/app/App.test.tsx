import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeaturedProduct } from '../shared/types/catalog'
import { App } from './App'

vi.mock('../features/catalog/services/catalogService', () => ({
  getFeaturedProducts: vi.fn(),
}))

vi.mock('../features/checkout/services/checkoutService', () => ({
  createOrderDraft: vi.fn(),
}))

import { getFeaturedProducts } from '../features/catalog/services/catalogService'
import { createOrderDraft } from '../features/checkout/services/checkoutService'

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

function renderAppAt(pathname = '/') {
  window.history.replaceState({}, '', pathname)
  return render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  )
}

describe('App checkout MVP flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()

    vi.mocked(getFeaturedProducts).mockResolvedValue({
      products: [featuredProduct],
      source: 'api',
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

  it('renders key mock-aligned shell sections', async () => {
    renderAppAt()

    expect(screen.getByRole('navigation', { name: 'Categorias' })).toBeInTheDocument()
    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Productos naturales El Tano' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Comprar ahora' })).toHaveAttribute(
      'href',
      '#productos-destacados-title',
    )
    expect(
      screen.queryByRole('heading', { name: 'Calidad real para todos los dias, a precio justo.' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Por que nos eligen' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Carrito' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Finalizar pedido' })).toBeInTheDocument()

    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
  })

  it('keeps required shell sections visible across viewport resize changes', async () => {
    renderAppAt()
    await screen.findByText('Almendra natural premium')

    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 375,
    })
    window.dispatchEvent(new Event('resize'))

    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Productos destacados' })).toBeInTheDocument()

    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 1280,
    })
    window.dispatchEvent(new Event('resize'))

    expect(screen.getByRole('heading', { name: 'Carrito' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Finalizar pedido' })).toBeInTheDocument()
  })

  it('keeps account icon as visual placeholder without auth actions', async () => {
    renderAppAt()
    await screen.findByText('Almendra natural premium')

    const iconContainer = screen.getByLabelText('Accesos de cuenta y carrito')

    expect(within(iconContainer).queryByRole('button')).not.toBeInTheDocument()
    expect(within(iconContainer).queryByRole('link')).not.toBeInTheDocument()
  })

  it('shows logo in header with graceful fallback text and keeps hero CTA target', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    const brandLogo = screen.getByRole('img', { name: 'El Tano Frutos Secos' })
    expect(brandLogo).toHaveAttribute('src', '/logo-el-tano.png')

    fireEvent.error(brandLogo)
    expect(screen.getByText('El Tano Frutos Secos')).toBeInTheDocument()

    const buyNowLink = screen.getByRole('link', { name: 'Comprar ahora' })
    expect(buyNowLink).toHaveAttribute('href', '#productos-destacados-title')

    expect(await screen.findByRole('heading', { name: 'Productos destacados' })).toHaveAttribute(
      'id',
      'productos-destacados-title',
    )
  })

  it('keeps search visual-only without side effects or catalog refresh', async () => {
    const user = userEvent.setup()

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    expect(vi.mocked(getFeaturedProducts)).toHaveBeenCalledTimes(1)

    const searchInput = screen.getByLabelText('Buscar productos')
    await user.type(searchInput, 'harina{enter}')

    expect(vi.mocked(getFeaturedProducts)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(createOrderDraft)).not.toHaveBeenCalled()
    expect(screen.getByText('Almendra natural premium')).toBeInTheDocument()
  })

  it('does not redirect to WhatsApp when draft API fails', async () => {
    const user = userEvent.setup()
    vi.mocked(createOrderDraft).mockRejectedValueOnce(new Error('backend down'))

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(window.open).not.toHaveBeenCalled()
    })

    expect(
      screen.getByText('No pudimos crear tu pedido. Intenta nuevamente en unos minutos.'),
    ).toBeInTheDocument()
  })

  it('blocks checkout submission when catalog is mock fallback', async () => {
    const user = userEvent.setup()
    vi.mocked(getFeaturedProducts).mockResolvedValueOnce({
      products: [
        {
          ...featuredProduct,
          variantId: 'mock-variant-1',
        },
      ],
      source: 'mock',
    })

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    expect(vi.mocked(createOrderDraft)).not.toHaveBeenCalled()
    expect(
      screen.getByText(
        'No podemos finalizar el pedido con productos de muestra. Espera a que cargue el catalogo real del backend.',
      ),
    ).toBeInTheDocument()
  })

  it('redirects to WhatsApp with encoded payload on successful draft creation', async () => {
    const user = userEvent.setup()
    vi.mocked(createOrderDraft).mockResolvedValueOnce({
      draftId: 'draft-2',
      reference: 'ET-2026-0002',
      currency: 'ARS',
      subtotal: 6400,
      total: 6400,
      whatsappMessage: 'Hola, confirmo pedido ET-2026-0002',
    })

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(window.open).toHaveBeenCalledTimes(1)
    })

    expect(window.open).toHaveBeenCalledWith(
      'https://wa.me/5491123456789?text=Hola%2C%20confirmo%20pedido%20ET-2026-0002',
      '_blank',
      'noopener,noreferrer',
    )
  })

  it('exposes navbar links Inicio, Categorias and Productos', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Categorias' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Productos' })).toBeInTheDocument()
  })

  it('navigates through required public routes via navbar and direct URL entry', async () => {
    const user = userEvent.setup()

    const view = renderAppAt('/categorias')

    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Productos' }))
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Categorias' }))
    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Inicio' }))
    expect(screen.getByRole('heading', { name: 'Por que nos eligen' })).toBeInTheDocument()

    view.unmount()
    renderAppAt('/categorias/frutos-secos')
    expect(screen.getByRole('heading', { name: 'Categoria: frutos-secos' })).toBeInTheDocument()
  })
})
