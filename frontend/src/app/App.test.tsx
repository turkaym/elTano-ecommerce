import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FeaturedProduct } from '../shared/types/catalog'
import { App } from './App'
import { CART_STORAGE_KEY } from '../features/cart/storage/cartStorage'

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

function getProductCard(productName: string) {
  const productCard = screen.getByRole('heading', { name: productName }).closest('article')
  expect(productCard).not.toBeNull()
  return productCard as HTMLElement
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
            { id: '11111111-1111-4111-8111-111111111111', unitLabel: 'bolsa 500 g', price: 6400, stockAvailable: 10 },
          ],
          isMultiVariant: false,
          minPrice: 6400,
          variantId: '11111111-1111-4111-8111-111111111111',
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
            { id: '22222222-2222-4222-8222-222222222222', unitLabel: 'bolsa 500 g', price: 5300, stockAvailable: 8 },
          ],
          isMultiVariant: false,
          minPrice: 5300,
          variantId: '22222222-2222-4222-8222-222222222222',
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
    expect(screen.queryByRole('search')).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Productos naturales El Tano' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Comprar ahora' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Calidad real para todos los dias, a precio justo.' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Por que nos eligen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Carrito' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Finalizar pedido' })).not.toBeInTheDocument()

    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
    expect(screen.getByText('Harina de coco organica')).toBeInTheDocument()
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

    expect(screen.queryByRole('search')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()

    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: 1280,
    })
    window.dispatchEvent(new Event('resize'))

    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()
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

  it('shows logo and icon actions while removing old central nav links', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    const brandLogo = screen.getByRole('img', { name: 'El Tano Frutos Secos' })
    expect(brandLogo).toHaveAttribute('src', '/elTanoLogo.png')
    expect(screen.getByRole('link', { name: 'Ir al inicio' })).toHaveAttribute('href', '/')

    fireEvent.error(brandLogo)
    expect(screen.getByText('El Tano Frutos Secos')).toBeInTheDocument()

    expect(screen.queryByRole('link', { name: 'Inicio' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Categorías' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Productos' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Accesos de cuenta y carrito')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver carrito, 0 items' })).toHaveAttribute('href', '/carrito')

    expect(await screen.findByRole('heading', { name: 'Productos' })).toHaveAttribute(
      'id',
      'productos-title',
    )
  })

  it('renders the full catalog and sidebar categories on the home route', async () => {
    renderAppAt('/')

    expect(await screen.findByRole('heading', { name: 'Productos' })).toBeInTheDocument()
    const sidebar = screen.getByRole('navigation', { name: 'Categorías' })
    expect(within(sidebar).getByRole('button', { name: /Todos los productos/i })).toHaveAttribute('aria-current', 'page')
    expect(within(sidebar).getByRole('button', { name: /Frutos secos/i })).toBeInTheDocument()
    expect(within(sidebar).getByRole('button', { name: /Harinas/i })).toBeInTheDocument()
    expect(screen.getByText('Almendra natural premium')).toBeInTheDocument()
    expect(screen.getByText('Harina de coco organica')).toBeInTheDocument()
  })

  it('filters home catalog products from the category sidebar', async () => {
    const user = userEvent.setup()
    renderAppAt('/')

    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
    const sidebar = screen.getByRole('navigation', { name: 'Categorías' })

    await user.click(within(sidebar).getByRole('button', { name: /Harinas/i }))

    expect(await screen.findByText('Harina de coco organica')).toBeInTheDocument()
    expect(screen.queryByText('Almendra natural premium')).not.toBeInTheDocument()
    expect(within(sidebar).getByRole('button', { name: /Harinas/i })).toHaveAttribute('aria-current', 'page')
  })

  it('restores product filter query when revisiting productos', async () => {
    const view = renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    expect(screen.queryByLabelText('Buscar productos')).not.toBeInTheDocument()

    view.unmount()
    renderAppAt('/productos?q=frutos%20secos')

    expect(screen.getByLabelText('Filtrar productos')).toHaveValue('frutos secos')
    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()
    expect(screen.queryByText('Harina de coco organica')).not.toBeInTheDocument()
  })

  it('keeps multi-word product filter text while typing', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    const filterInput = screen.getByLabelText('Filtrar productos')

    await user.type(filterInput, 'frutos secos')

    expect(filterInput).toHaveValue('frutos secos')
    expect(window.location.search).toContain('q=frutos+secos')
  })

  it('does not render the removed navbar search on non-product pages', () => {
    renderAppAt('/categorias')

    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Buscar productos')).not.toBeInTheDocument()
  })

  it('requires variant selection and sends selected variant + quantity to checkout draft', async () => {
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

    expect(await screen.findByText(/\$\s*11.800/i)).toBeInTheDocument()
    const productCard = getProductCard('Almendra natural premium')
    expect(within(productCard).getByLabelText('Presentacion para Almendra natural premium')).toHaveDisplayValue('bolsa 1 kg')

    await user.selectOptions(
      within(productCard).getByLabelText('Presentacion para Almendra natural premium'),
      '22222222-2222-4222-8222-222222222222',
    )
    fireEvent.change(within(productCard).getByLabelText('Cantidad para Almendra natural premium'), {
      target: { value: '3' },
    })
    await user.click(within(productCard).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 3 items' }))

    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(vi.mocked(createOrderDraft)).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ variantId: '22222222-2222-4222-8222-222222222222', quantity: 3 }],
          fulfillmentMethod: 'PICKUP',
          pickupTime: '18:30',
        }),
      )
    })
  })

  it('renders cart page cards with metadata and preserves checkout draft item payload shape', async () => {
    const user = userEvent.setup()
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      source: 'api',
      items: [
        {
          ...featuredProduct,
          categorySlug: 'frutos-secos',
          primaryImageUrl: '/uploads/almendra.jpg',
          primaryImageAltText: 'Almendra premium en bolsa',
        },
      ],
    })

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))

    const cartItem = screen.getByRole('listitem', { name: /Almendra natural premium/i })
    expect(within(cartItem).getByRole('img', { name: 'Almendra premium en bolsa' })).toBeInTheDocument()
    expect(within(cartItem).getByText('Frutos secos')).toBeInTheDocument()
    expect(within(cartItem).getByText('bolsa 500 g')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Seguir comprando' })).toHaveAttribute('href', '/productos')

    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(createOrderDraft).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ variantId: '11111111-1111-4111-8111-111111111111', quantity: 1 }],
          fulfillmentMethod: 'PICKUP',
          pickupTime: '18:30',
        }),
      )
    })
  })

  it('allows checkout from a directly opened persisted cart with valid backend variant ids', async () => {
    const user = userEvent.setup()
    window.localStorage.setItem(
      CART_STORAGE_KEY,
      JSON.stringify([
        {
          variantId: '11111111-1111-4111-8111-111111111111',
          productName: 'Almendra natural premium',
          unitLabel: 'bolsa 500 g',
          price: 6400,
          quantity: 1,
          stockAvailable: 10,
          categoryName: 'Frutos secos',
        },
      ]),
    )

    renderAppAt('/carrito')

    expect(screen.getByRole('heading', { name: 'Mi carrito' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(createOrderDraft).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [{ variantId: '11111111-1111-4111-8111-111111111111', quantity: 1 }],
          fulfillmentMethod: 'PICKUP',
          pickupTime: '18:30',
        }),
      )
    })
  })

  it('updates the shared cart badge when adding from productos cards', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    const productCard = getProductCard('Almendra natural premium')

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

  it('updates the shared cart badge when adding from a category detail card', async () => {
    const user = userEvent.setup()

    renderAppAt('/categorias/frutos-secos')

    await screen.findByRole('heading', { name: 'Categoria: Frutos secos' })
    const productCard = getProductCard('Almendra natural premium')

    await user.click(within(productCard!).getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Ver carrito, 1 item' })).toHaveTextContent('1')
    })
  })

  it('does not render a header cart badge when the cart is empty', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    expect(screen.getByRole('link', { name: 'Ver carrito, 0 items' })).toHaveAttribute('href', '/carrito')
    expect(screen.queryByText('0')).not.toBeInTheDocument()
  })

  it('renders the dedicated cart route with empty state and checkout disabled', async () => {
    renderAppAt('/carrito')

    expect(screen.getByRole('heading', { name: 'Mi carrito' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Tu carrito está vacío' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver productos' })).toHaveAttribute('href', '/productos')
    expect(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' })).toBeDisabled()
  })

  it('keeps home catalog add-to-cart updating the shared cart badge', async () => {
    const user = userEvent.setup()

    renderAppAt('/')

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(
        screen.getByRole('link', { name: 'Ver carrito, 1 item' }),
      ).toHaveTextContent('1')
    })
  })

  it('navigates from the header cart icon to the cart page from productos', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 0 items' }))

    await waitFor(() => {
      expect(window.location.pathname).toBe('/carrito')
    })
    expect(screen.getByRole('heading', { name: 'Mi carrito' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Tu carrito está vacío' })).toBeInTheDocument()
  })

  it('navigates back to the catalog from the header logo and cart continue-shopping action', async () => {
    const user = userEvent.setup()

    renderAppAt('/')

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))

    await waitFor(() => {
      expect(window.location.pathname).toBe('/carrito')
    })

    await user.click(screen.getByRole('link', { name: 'Seguir comprando' }))
    await waitFor(() => {
      expect(window.location.pathname).toBe('/productos')
    })

    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await screen.findByRole('heading', { name: 'Mi carrito' })
    await user.click(screen.getByRole('link', { name: 'Ir al inicio' }))

    await waitFor(() => {
      expect(window.location.pathname).toBe('/')
    })
    expect(await screen.findByRole('heading', { name: 'Productos' })).toBeInTheDocument()
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
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(window.open).not.toHaveBeenCalled()
    })

    expect(
      screen.getByText('No pudimos crear tu pedido. Intenta nuevamente en unos minutos.'),
    ).toBeInTheDocument()
  })

  it('blocks checkout submission when cart items have non-backend variant ids', async () => {
    const user = userEvent.setup()
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      items: [
        {
          ...featuredProduct,
          categorySlug: 'frutos-secos',
          variantId: 'mock-variant-1',
          variants: [
            { id: 'mock-variant-1', unitLabel: 'bolsa 500 g', price: 6400, stockAvailable: 10 },
          ],
        },
      ],
      source: 'mock',
    })

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Juan Perez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    expect(vi.mocked(createOrderDraft)).not.toHaveBeenCalled()
    expect(
      screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }),
    ).toBeDisabled()
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
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')

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

  it('does not show a false WhatsApp popup error when noopener returns null', async () => {
    const user = userEvent.setup()
    vi.mocked(window.open).mockReturnValueOnce(null)

    renderAppAt()

    await screen.findByText('Almendra natural premium')
    await user.click(within(getProductCard('Almendra natural premium')).getByRole('button', { name: 'Agregar' }))
    await user.click(screen.getByRole('link', { name: 'Ver carrito, 1 item' }))
    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Ana Lopez')
    await user.type(screen.getByLabelText('Telefono *'), '+5491198765432')
    await user.type(screen.getByLabelText('Horario aproximado de retiro *'), '18:30')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    await waitFor(() => {
      expect(window.open).toHaveBeenCalledTimes(1)
    })
    expect(screen.queryByText('No pudimos abrir WhatsApp automaticamente. Habilita popups e intenta de nuevo.')).not.toBeInTheDocument()
  })

  it('does not expose old navbar links Inicio, Categorías and Productos', async () => {
    renderAppAt()

    await screen.findByText('Almendra natural premium')

    expect(screen.queryByRole('link', { name: 'Inicio' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Categorías' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Productos' })).not.toBeInTheDocument()
  })

  it('marks only the current sidebar category as active', async () => {
    const user = userEvent.setup()

    renderAppAt('/productos')

    await screen.findByText('Almendra natural premium')
    const sidebar = screen.getByRole('navigation', { name: 'Categorías' })
    const allProductsButton = within(sidebar).getByRole('button', { name: /Todos los productos/i })
    const harinasButton = within(sidebar).getByRole('button', { name: /Harinas/i })

    expect(allProductsButton).toHaveAttribute('aria-current', 'page')
    expect(harinasButton).not.toHaveAttribute('aria-current')

    await user.click(harinasButton)

    expect(harinasButton).toHaveAttribute('aria-current', 'page')
    expect(allProductsButton).not.toHaveAttribute('aria-current')
  })

  it('keeps required public routes available by direct URL entry', async () => {
    let view = renderAppAt('/categorias')

    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Frutos secos/i })).toHaveAttribute(
      'href',
      '/categorias/frutos-secos',
    )

    view.unmount()
    view = renderAppAt('/productos')
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()
    expect(await screen.findByText('Almendra natural premium')).toBeInTheDocument()

    view.unmount()
    view = renderAppAt('/')
    expect(screen.queryByRole('heading', { name: 'Por que nos eligen' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Productos' })).toBeInTheDocument()

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

  it('keeps checkout return route render isolated from catalog search', () => {
    renderAppAt('/checkout/return')

    expect(screen.getByRole('heading', { name: 'Pago online no disponible' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Buscar productos')).not.toBeInTheDocument()
  })
})
