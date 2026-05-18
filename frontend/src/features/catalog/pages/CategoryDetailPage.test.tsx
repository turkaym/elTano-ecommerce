import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CART_STORAGE_KEY } from '../../cart/storage/cartStorage'
import { CategoryDetailPage } from './CategoryDetailPage'

vi.mock('../hooks/useCatalogQuery', () => ({
  useCatalogQuery: vi.fn(),
}))

import { useCatalogQuery } from '../hooks/useCatalogQuery'

describe('CategoryDetailPage', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('renders only products for the selected valid category slug', () => {
    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      categories: [
        { name: 'Frutos secos', slug: 'frutos-secos', count: 2 },
        { name: 'Harinas', slug: 'harinas', count: 1 },
      ],
      items: [
        {
          id: '1',
          name: 'Almendra tostada',
          description: 'Snack natural',
          categoryName: 'Frutos secos',
          categorySlug: 'frutos-secos',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [{ id: 'v1', unitLabel: 'bolsa 500 g', price: 5200, stockAvailable: 10 }],
          isMultiVariant: false,
          minPrice: 5200,
          variantId: 'v1',
          unitLabel: 'bolsa 500 g',
          price: 5200,
          stockAvailable: 10,
          primaryImageUrl: null,
          primaryImageAltText: null,
        },
      ],
    })

    render(
      <MemoryRouter initialEntries={['/categorias/frutos-secos']}>
        <Routes>
          <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Categoria: Frutos secos' })).toBeInTheDocument()
    expect(screen.getByText('Almendra tostada')).toBeInTheDocument()
    expect(screen.queryByText('Harina de coco')).not.toBeInTheDocument()
  })

  it('renders deterministic empty state for invalid category slug', () => {
    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      categories: [{ name: 'Frutos secos', slug: 'frutos-secos', count: 2 }],
      items: [],
    })

    render(
      <MemoryRouter initialEntries={['/categorias/categoria-invalida']}>
        <Routes>
          <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Categoria no encontrada' })).toBeInTheDocument()
    expect(
      screen.getByText('No encontramos una categoria para el slug "categoria-invalida".'),
    ).toBeInTheDocument()
    expect(screen.queryByText('Almendra tostada')).not.toBeInTheDocument()
  })

  it('suppresses customer-facing stock counters in category detail runtime rendering', () => {
    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      categories: [{ name: 'Frutos secos', slug: 'frutos-secos', count: 1 }],
      items: [
        {
          id: '1',
          name: 'Mix premium',
          description: 'Blend tostado',
          categoryName: 'Frutos secos',
          categorySlug: 'frutos-secos',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [{ id: 'v1', unitLabel: 'bolsa 500 g', price: 5200, stockAvailable: 10 }],
          isMultiVariant: false,
          minPrice: 5200,
          variantId: 'v1',
          unitLabel: 'bolsa 500 g',
          price: 5200,
          stockAvailable: 10,
          primaryImageUrl: null,
          primaryImageAltText: null,
        },
      ],
    })

    render(
      <MemoryRouter initialEntries={['/categorias/frutos-secos']}>
        <Routes>
          <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Mix premium')).toBeInTheDocument()
    expect(screen.queryByText(/stock disponible/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/quedan\s+\d+/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/\d+\s*unidades/i)).not.toBeInTheDocument()
  })

  it('adds category detail products to cart with image and category metadata', async () => {
    const user = userEvent.setup()
    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      categories: [{ name: 'Frutos secos', slug: 'frutos-secos', count: 1 }],
      items: [
        {
          id: 'prod-1',
          name: 'Mix premium',
          description: 'Blend tostado',
          categoryName: 'Frutos secos',
          categorySlug: 'frutos-secos',
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [{ id: 'v1', unitLabel: 'bolsa 500 g', price: 5200, stockAvailable: 10 }],
          isMultiVariant: false,
          minPrice: 5200,
          variantId: 'v1',
          unitLabel: 'bolsa 500 g',
          price: 5200,
          stockAvailable: 10,
          primaryImageUrl: 'https://cdn.example.test/mix.jpg',
          primaryImageAltText: 'Mix premium en bolsa',
        },
      ],
    })

    render(
      <MemoryRouter initialEntries={['/categorias/frutos-secos']}>
        <Routes>
          <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(within(screen.getByRole('heading', { name: 'Mix premium' }).closest('article')!).getByRole('button', { name: 'Agregar' }))

    await waitFor(() => {
      expect(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? '[]')).toEqual([
        expect.objectContaining({
          variantId: 'v1',
          productId: 'prod-1',
          productName: 'Mix premium',
          categoryName: 'Frutos secos',
          imageUrl: 'https://cdn.example.test/mix.jpg',
          imageAltText: 'Mix premium en bolsa',
          quantity: 1,
        }),
      ])
    })
  })
})
