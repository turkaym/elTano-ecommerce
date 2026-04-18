import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { CategoriesPage } from './CategoriesPage'

vi.mock('../hooks/useCatalogQuery', () => ({
  useCatalogQuery: vi.fn(),
}))

import { useCatalogQuery } from '../hooks/useCatalogQuery'

describe('CategoriesPage', () => {
  it('lists all available categories and allows click-through to slug route', async () => {
    const user = userEvent.setup()

    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      items: [],
      categories: [
        { name: 'Frutos secos', slug: 'frutos-secos', count: 2 },
        { name: 'Harinas', slug: 'harinas', count: 1 },
      ],
    })

    render(
      <MemoryRouter initialEntries={['/categorias']}>
        <Routes>
          <Route path="/categorias" element={<CategoriesPage />} />
          <Route path="/categorias/:slug" element={<h2>Detalle categoria</h2>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Categorias' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Frutos secos/i })).toHaveAttribute(
      'href',
      '/categorias/frutos-secos',
    )
    expect(screen.getByRole('link', { name: /Harinas/i })).toHaveAttribute('href', '/categorias/harinas')

    await user.click(screen.getByRole('link', { name: /Frutos secos/i }))

    expect(screen.getByRole('heading', { name: 'Detalle categoria' })).toBeInTheDocument()
  })

  it('shows deterministic empty state when no categories are available', () => {
    vi.mocked(useCatalogQuery).mockReturnValue({
      source: 'api',
      isLoading: false,
      items: [],
      categories: [],
    })

    render(
      <MemoryRouter initialEntries={['/categorias']}>
        <Routes>
          <Route path="/categorias" element={<CategoriesPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('No hay categorias disponibles en este momento.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Frutos secos/i })).not.toBeInTheDocument()
  })
})
