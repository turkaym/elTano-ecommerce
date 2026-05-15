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

vi.mock('../features/catalog/services/catalogQueryService', () => ({
  getCatalogListItems: vi.fn(),
}))

import { getFeaturedProducts } from '../features/catalog/services/catalogService'
import { getCatalogListItems } from '../features/catalog/services/catalogQueryService'
import { createOrderDraft } from '../features/checkout/services/checkoutService'

const featuredProduct: FeaturedProduct = {
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
  stockAvailable: 10,
  variantId: '11111111-1111-4111-8111-111111111111',
  unitLabel: 'bolsa 500 g',
  price: 6400,
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
            { id: 'var-1', unitLabel: 'bolsa 500 g', price: 6400, stockAvailable: 10 },
          ],
          isMultiVariant: false,
          minPrice: 6400,
          variantId: 'var-1',
          unitLabel: 'bolsa 500 g',
          price: 6400,
          stockAvailable: 10,
        },
        {
          id: 'prod-2',
          name: 'Harina de coco organica',
          description: 'Alternativa baja en carbohidratos.',
          categoryName: 'Harinas',
          categorySlug: 'harinas',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [
            { id: 'var-2', unitLabel: 'bolsa 500 g', price: 5300, stockAvailable: 8 },
          ],
          isMultiVariant: false,
          minPrice: 5300,
          variantId: 'var-2',
          unitLabel: 'bolsa 500 g',
          price: 5300,
          stockAvailable: 8,
        },
      ],
    })

    vi.spyOn(window, 'open').mockImplementation(() => ({ closed: false }) as Window)
  })

  it('renders key mock-aligned shell sections', async () => {
    renderAppAt()

    expect(screen.getByRole('navigation', { name: 'Categorías' })).toBeInTheDocument()
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

  it('does not expose customer-facing stock counters in featured cards', async () => {
    renderAppAt()
    await screen.findByText('Almendra natural premium')

    expect(screen.queryByText(/stock disponible/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/quedan\s+\d+/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/\d+\s*unidades/i)).not.toBeInTheDocument()
  })

  it('keeps account icon as visual placeholder without auth actions', async () => {
    renderAppAt()
    await screen.findByText('Almendra natural premium')

    const iconContainer = screen.getByLabelText('Accesos de cuenta y carrito')

    expect(within(iconContainer).queryByLabelText(/cuenta/i)).not.toBeInTheDocument()
  })

  it('shows logo in header with graceful fallback text and keeps hero CTA target', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    const brandLogo = screen.getByRole('img', { name: 'El Tano Frutos Secos' })
    expect(brandLogo).toHaveAttribute('src', '/elTanoLogo.png')

    fireEvent.error(brandLogo)
    expect(screen.getByText('El Tano Frutos Secos')).toBeInTheDocument()

    const buyNowLink = screen.getByRole('link', { name: 'Comprar ahora' })
    expect(buyNowLink).toHaveAttribute('href', '#productos-destacados-title')

    expect(await screen.findByRole('heading', { name: 'Productos destacados' })).toHaveAttribute(
      'id',
      'productos-destacados-title',
    )
  })

  it('persists search query in URL and restores it when revisiting productos', async () => {
    const user = userEvent.setup()

    const view = renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')

    const searchInput = screen.getByLabelText('Buscar productos')
    await user.type(searchInput, 'harina')

    await waitFor(() => {
      expect(window.location.search).toContain('q=harina')
    })

    expect(await screen.findByText('Harina de coco organica')).toBeInTheDocument()
    expect(screen.queryByText('Almendra natural premium')).not.toBeInTheDocument()

    view.unmount()
    renderAppAt('/productos?q=frutos%20secos')

    expect(await screen.findByDisplayValue('frutos secos')).toBeInTheDocument()
    expect(screen.getByText('Almendra natural premium')).toBeInTheDocument()
    expect(screen.queryByText('Harina de coco organica')).not.toBeInTheDocument()
  })

  it('requires variant selection and sends selected variant + quantity to checkout draft', async () => {
    const user = userEvent.setup()

    vi.mocked(getFeaturedProducts).mockResolvedValueOnce({
      source: 'api',
      products: [
        {
          ...featuredProduct,
          variantId: null,
          unitLabel: 'Seleccionar presentación',
          isMultiVariant: true,
          minPrice: 6400,
          price: 6400,
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
          stockAvailable: 17,
        },
      ],
    })

    renderAppAt()

    expect(await screen.findByText(/Desde\s*\$\s*6.400/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Agregar' }))
    expect(screen.getByText('Selecciona una variante para continuar.')).toBeInTheDocument()

    await user.selectOptions(
      screen.getByLabelText('Presentacion para Almendra natural premium'),
      '22222222-2222-4222-8222-222222222222',
    )
    fireEvent.change(screen.getByLabelText('Cantidad para Almendra natural premium'), {
      target: { value: '3' },
    })
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(vi.mocked(createOrderDraft)).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ variantId: '22222222-2222-4222-8222-222222222222', quantity: 3 }],
        }),
      )
    })
  })

  it('updates the shared cart badge when adding from productos cards', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    const productCard = screen.getByRole('heading', { name: 'Almendra natural premium' }).closest('article')
    expect(productCard).not.toBeNull()

    fireEvent.change(within(productCard!).getByLabelText('Cantidad para Almendra natural premium'), {
      target: { value: '2' },
    })
    await user.click(within(productCard!).getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(
        screen.getByRole('link', { name: 'Ver carrito, 2 items' }),
      ).toHaveTextContent('2')
    })
  })

  it('does not render a header cart badge when the cart is empty', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    expect(screen.getByRole('link', { name: 'Ver carrito, 0 items' })).toBeInTheDocument()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })

  it('keeps home featured add-to-cart updating the shared cart badge', async () => {
    const user = userEvent.setup()

    renderAppAt('/')

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(
        screen.getByRole('link', { name: 'Ver carrito, 1 item' }),
      ).toHaveTextContent('1')
    })
  })

  it('navigates from the header cart icon to the home cart section from productos', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 0 items' }))

    await waitFor(() => {
      expect(window.location.pathname).toBe('/')
      expect(window.location.hash).toBe('#carrito')
    })
    expect(screen.getByRole('heading', { name: 'Carrito' })).toHaveFocus()
  })

  it('does not update the shared cart badge for out-of-stock productos cards', async () => {
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      source: 'api',
      items: [
        {
          id: 'prod-out',
          name: 'Harina sin stock',
          description: 'No disponible',
          categoryName: 'Harinas',
          categorySlug: 'harinas',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [
            { id: 'var-out', unitLabel: 'bolsa 500 g', price: 5300, stockAvailable: 0 },
          ],
          isMultiVariant: false,
          minPrice: 5300,
          variantId: 'var-out',
          unitLabel: 'bolsa 500 g',
          price: 5300,
          stockAvailable: 0,
        },
      ],
    })

    renderAppAt('/productos')

    await screen.findByText('Harina sin stock')

    expect(screen.getByRole('button', { name: 'Sin stock' })).toBeDisabled()
    expect(within(screen.getByLabelText('Accesos de cuenta y carrito')).queryByText('1')).not.toBeInTheDocument()
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
      'https://wa.me/5492966659577?text=Hola%2C%20confirmo%20pedido%20ET-2026-0002',
      '_blank',
      'noopener,noreferrer',
    )
  })

  it('exposes navbar links Inicio, Categorías and Productos', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    expect(screen.getByRole('link', { name: 'Inicio' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Categorías' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Productos' })).toBeInTheDocument()
  })

  it('marks only the current route link as active in navbar', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    const inicioLink = screen.getByRole('link', { name: 'Inicio' })
    const categoriasLink = screen.getByRole('link', { name: 'Categorías' })
    const productosLink = screen.getByRole('link', { name: 'Productos' })

    expect(productosLink).toHaveAttribute('aria-current', 'page')
    expect(inicioLink).not.toHaveAttribute('aria-current')
    expect(categoriasLink).not.toHaveAttribute('aria-current')

    await user.click(categoriasLink)

    expect(categoriasLink).toHaveAttribute('aria-current', 'page')
    expect(inicioLink).not.toHaveAttribute('aria-current')
    expect(productosLink).not.toHaveAttribute('aria-current')
  })

  it('navigates through required public routes via navbar and direct URL entry', async () => {
    const user = userEvent.setup()

    const view = renderAppAt('/categorias')

    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Frutos secos/i })).toHaveAttribute(
      'href',
      '/categorias/frutos-secos',
    )

    await user.click(screen.getByRole('link', { name: 'Productos' }))
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Categorías' }))
    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Inicio' }))
    expect(screen.getByRole('heading', { name: 'Por que nos eligen' })).toBeInTheDocument()

    view.unmount()
    renderAppAt('/categorias/frutos-secos')
    expect(await screen.findByRole('heading', { name: 'Categoria: Frutos secos' })).toBeInTheDocument()
    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
  })

  it('shows deterministic empty state for invalid category slug route', async () => {
    renderAppAt('/categorias/categoria-invalida')

    expect(await screen.findByRole('heading', { name: 'Categoria no encontrada' })).toBeInTheDocument()
    expect(
      screen.getByText('No encontramos una categoria para el slug "categoria-invalida".'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Almendra natural premium')).not.toBeInTheDocument()
  })

  it('keeps catalog navigation working after checkout return route render', async () => {
    const user = userEvent.setup()

    renderAppAt('/checkout/return')

    expect(screen.getByRole('heading', { name: 'Pago online no disponible' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Productos' }))
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()
    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Categorías' }))
    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
  })
})
