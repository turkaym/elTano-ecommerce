import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CatalogListItem } from '../../../shared/types/catalog'
import { CART_STORAGE_KEY } from '../../cart/storage/cartStorage'
import { ProductsPage } from './ProductsPage'

vi.mock('../services/catalogQueryService', () => ({
  getCatalogListItems: vi.fn(),
}))

import { getCatalogListItems } from '../services/catalogQueryService'

const catalogItems: CatalogListItem[] = [
  {
    id: 'prod-1',
    name: 'Almendra tostada',
    description: 'Snack natural',
    categoryName: 'Frutos secos',
    categorySlug: 'frutos-secos',
    productType: 'ENVASADO',
    inventoryPolicy: 'PER_VARIANT',
    variants: [{ id: 'var-1', unitLabel: 'bolsa 500 g', price: 6800, stockAvailable: 10 }],
    isMultiVariant: false,
    minPrice: 6800,
    variantId: 'var-1',
    unitLabel: 'bolsa 500 g',
    price: 6800,
    stockAvailable: 10,
    primaryImageUrl: 'https://cdn.example.test/almendra.jpg',
    primaryImageAltText: 'Almendra tostada en bolsa',
  },
  {
    id: 'prod-2',
    name: 'Harina de coco',
    description: 'Ideal para reposteria',
    categoryName: 'Harinas',
    categorySlug: 'harinas',
    productType: 'ENVASADO',
    inventoryPolicy: 'PER_VARIANT',
    variants: [{ id: 'var-2', unitLabel: 'bolsa 500 g', price: 5200, stockAvailable: 0 }],
    isMultiVariant: false,
    minPrice: 5200,
    variantId: 'var-2',
    unitLabel: 'bolsa 500 g',
    price: 5200,
    stockAvailable: 0,
    primaryImageUrl: null,
    primaryImageAltText: null,
  },
  {
    id: 'prod-3',
    name: 'Nuez mariposa',
    description: 'Para ensaladas',
    categoryName: 'Frutos secos',
    categorySlug: 'frutos-secos',
    productType: 'ENVASADO',
    inventoryPolicy: 'PER_VARIANT',
    variants: [{ id: 'var-3', unitLabel: 'bolsa 500 g', price: 7600, stockAvailable: 6 }],
    isMultiVariant: false,
    minPrice: 7600,
    variantId: 'var-3',
    unitLabel: 'bolsa 500 g',
    price: 7600,
    stockAvailable: 6,
    primaryImageUrl: null,
    primaryImageAltText: null,
  },
]

function renderProductsAt(pathname = '/productos') {
  window.history.replaceState({}, '', pathname)
  return render(
    <BrowserRouter>
      <Routes>
        <Route path="/productos" element={<ProductsPage />} />
      </Routes>
    </BrowserRouter>,
  )
}

describe('ProductsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    vi.mocked(getCatalogListItems).mockResolvedValue({
      source: 'api',
      items: catalogItems,
    })
  })

  it('shows all products sorted A-Z by default', async () => {
    renderProductsAt('/productos')

    const productHeadings = await screen.findAllByRole('heading', { level: 3 })
    expect(productHeadings.map((heading) => heading.textContent)).toEqual([
      'Almendra tostada',
      'Harina de coco',
      'Nuez mariposa',
    ])
  })

  it('applies category, stock and sort controls and persists them in URL params', async () => {
    const user = userEvent.setup()

    renderProductsAt('/productos')
    await screen.findByText('Almendra tostada')

    await user.selectOptions(screen.getByLabelText('Categorías'), 'frutos-secos')
    await user.selectOptions(screen.getByLabelText('Stock'), 'in-stock')
    await user.selectOptions(screen.getByLabelText('Ordenar'), 'price-desc')

    await waitFor(() => {
      expect(window.location.search).toContain('category=frutos-secos')
      expect(window.location.search).toContain('stock=in-stock')
      expect(window.location.search).toContain('sort=price-desc')
    })

    const filteredHeadings = await screen.findAllByRole('heading', { level: 3 })
    expect(filteredHeadings.map((heading) => heading.textContent)).toEqual([
      'Nuez mariposa',
      'Almendra tostada',
    ])
    expect(screen.queryByText('Harina de coco')).not.toBeInTheDocument()
  })

  it('filters from all products with sidebar categories and restores the full list', async () => {
    const user = userEvent.setup()

    renderProductsAt('/productos')
    await screen.findByText('Almendra tostada')

    await user.click(screen.getByRole('button', { name: 'Harinas (1)' }))

    await waitFor(() => expect(window.location.search).toContain('category=harinas'))
    expect(screen.getByText('Harina de coco')).toBeInTheDocument()
    expect(screen.queryByText('Almendra tostada')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Todos los productos (3)' }))

    await waitFor(() => expect(window.location.search).not.toContain('category='))
    expect(screen.getByText('Almendra tostada')).toBeInTheDocument()
    expect(screen.getByText('Nuez mariposa')).toBeInTheDocument()
  })

  it('updates URL-backed search results in real time while preserving the category filter', async () => {
    const user = userEvent.setup()

    renderProductsAt('/productos?category=frutos-secos')
    await screen.findByText('Almendra tostada')

    await user.type(screen.getByLabelText('Filtrar productos'), 'nuez')

    await waitFor(() => expect(window.location.search).toContain('q=nuez'))
    expect(screen.getByText('Nuez mariposa')).toBeInTheDocument()
    expect(screen.queryByText('Almendra tostada')).not.toBeInTheDocument()
    expect(screen.queryByText('Harina de coco')).not.toBeInTheDocument()

    await user.clear(screen.getByLabelText('Filtrar productos'))

    await waitFor(() => expect(window.location.search).not.toContain('q='))
    expect(screen.getByText('Almendra tostada')).toBeInTheDocument()
    expect(screen.getByText('Nuez mariposa')).toBeInTheDocument()
  })

  it('reads query params from URL as source of truth for search and empty state', async () => {
    renderProductsAt('/productos?q=harinas&stock=in-stock')

    expect(await screen.findByRole('heading', { name: 'Sin resultados' })).toBeInTheDocument()
    expect(screen.getByText('No encontramos productos para mostrar en este momento.')).toBeInTheDocument()
    expect(screen.queryByText('Almendra tostada')).not.toBeInTheDocument()
  })

  it('restores filter state when navigating browser back/forward history', async () => {
    const user = userEvent.setup()

    renderProductsAt('/productos')
    await screen.findByText('Almendra tostada')

    const stockSelect = screen.getByLabelText('Stock')
    await user.selectOptions(stockSelect, 'in-stock')

    await waitFor(() => {
      expect(window.location.search).toContain('stock=in-stock')
    })
    expect(screen.queryByText('Harina de coco')).not.toBeInTheDocument()

    await user.selectOptions(stockSelect, 'all')
    await waitFor(() => {
      expect(window.location.search).not.toContain('stock=in-stock')
    })
    expect(await screen.findByText('Harina de coco')).toBeInTheDocument()

    await act(async () => {
      window.history.back()
      window.dispatchEvent(new PopStateEvent('popstate'))
    })

    await waitFor(() => {
      expect(screen.getByLabelText('Stock')).toHaveValue('in-stock')
    })
    expect(screen.queryByText('Harina de coco')).not.toBeInTheDocument()
  })

  it('renders multi-variant cards with "Desde" price semantics', async () => {
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      source: 'api',
      items: [
        {
          id: 'prod-desde',
          name: 'Mix granola premium',
          description: 'Version multi-formato',
          categoryName: 'Granolas',
          categorySlug: 'granolas',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [
            { id: 'var-a', unitLabel: 'bolsa 250 g', price: 3900, stockAvailable: 5 },
            { id: 'var-b', unitLabel: 'bolsa 500 g', price: 7100, stockAvailable: 4 },
          ],
          isMultiVariant: true,
          minPrice: 3900,
          variantId: null,
          unitLabel: 'Seleccionar presentación',
          price: 3900,
          stockAvailable: 9,
          primaryImageUrl: null,
          primaryImageAltText: null,
        },
      ],
    })

    renderProductsAt('/productos')

    expect(await screen.findByText(/Desde\s*\$\s*3.900/i)).toBeInTheDocument()
  })

  it('lets shoppers add an in-stock productos card to the cart', async () => {
    const user = userEvent.setup()
    renderProductsAt('/productos')

    await screen.findByText('Almendra tostada')
    await user.click(within(screen.getByRole('heading', { name: 'Almendra tostada' }).closest('article')!).getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? '[]')).toEqual([
        expect.objectContaining({
          variantId: 'var-1',
          productName: 'Almendra tostada',
          unitLabel: 'bolsa 500 g',
          price: 6800,
          quantity: 1,
          stockAvailable: 10,
          productId: 'prod-1',
          categoryName: 'Frutos secos',
          imageUrl: 'https://cdn.example.test/almendra.jpg',
          imageAltText: 'Almendra tostada en bolsa',
        }),
      ])
    })
  })

  it('supports variant and quantity selection on productos cards and keeps out-of-stock variants unavailable', async () => {
    const user = userEvent.setup()
    vi.mocked(getCatalogListItems).mockResolvedValueOnce({
      source: 'api',
      items: [
        {
          id: 'prod-mix',
          name: 'Mix semillas',
          description: 'Multi presentación',
          categoryName: 'Semillas',
          categorySlug: 'semillas',
          productType: 'GRANEL',
          inventoryPolicy: 'BULK_WEIGHT',
          stockAvailableBaseGrams: 300,
          variants: [
            { id: 'mix-100', unitLabel: '100g', price: 1200, stockAvailable: 3 },
            { id: 'mix-500', unitLabel: '500g', price: 6000, stockAvailable: 0 },
          ],
          isMultiVariant: true,
          minPrice: 1200,
          variantId: null,
          unitLabel: 'Seleccionar presentación',
          price: 1200,
          stockAvailable: 3,
          primaryImageUrl: null,
          primaryImageAltText: null,
        },
      ],
    })

    renderProductsAt('/productos')

    await screen.findByText('Mix semillas')
    expect(screen.getByRole('option', { name: '500g - sin stock' })).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Presentacion para Mix semillas'), 'mix-100')
    fireEvent.change(screen.getByLabelText('Cantidad para Mix semillas'), { target: { value: '2' } })
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? '[]')).toEqual([
        expect.objectContaining({
          variantId: 'mix-100',
          productName: 'Mix semillas',
          unitLabel: '100g',
          price: 1200,
          quantity: 2,
          stockAvailable: 3,
        }),
      ])
    })
  })
})
