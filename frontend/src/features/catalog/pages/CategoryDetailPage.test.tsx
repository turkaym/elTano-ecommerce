import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { CategoryDetailPage } from './CategoryDetailPage'

vi.mock('../hooks/useCatalogQuery', () => ({
  useCatalogQuery: vi.fn(),
}))

import { useCatalogQuery } from '../hooks/useCatalogQuery'

describe('CategoryDetailPage', () => {
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
})
