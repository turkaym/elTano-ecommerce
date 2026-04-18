import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { CatalogProduct } from '../../../shared/types/catalog'
import { useCatalogQuery } from './useCatalogQuery'

vi.mock('../../../shared/api/httpClient', () => ({
  getJson: vi.fn(),
}))

import { getJson } from '../../../shared/api/httpClient'

const apiProducts: CatalogProduct[] = [
  {
    id: 'prod-1',
    name: 'Almendra natural premium',
    slug: 'almendra-natural-premium',
    description: 'Ideal para snack',
    categoryName: 'Frutos secos',
    categorySlug: 'frutos-secos',
    variants: [
      {
        id: 'var-1',
        sku: 'SKU-1',
        unitType: 'weight',
        weightGrams: 500,
        unitLabel: 'bolsa 500 g',
        price: 6400,
        stockAvailable: 10,
        stockReserved: 0,
        attributesJson: null,
      },
    ],
  },
]

describe('useCatalogQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads from API when catalog data is available', async () => {
    vi.mocked(getJson).mockResolvedValueOnce(apiProducts)

    const { result } = renderHook(() =>
      useCatalogQuery({
        q: '',
        category: null,
        stock: 'all',
        sort: 'name-asc',
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.source).toBe('api')
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0]?.name).toBe('Almendra natural premium')
  })

  it('falls back to mock items when API request fails', async () => {
    vi.mocked(getJson).mockRejectedValueOnce(new Error('network'))

    const { result } = renderHook(() =>
      useCatalogQuery({
        q: '',
        category: null,
        stock: 'all',
        sort: 'name-asc',
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.source).toBe('mock')
    expect(result.current.items.length).toBeGreaterThan(0)
    expect(result.current.categories.length).toBeGreaterThan(0)
  })

  it('falls back to mock items when API returns an empty list', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([])

    const { result } = renderHook(() =>
      useCatalogQuery({
        q: '',
        category: null,
        stock: 'all',
        sort: 'name-asc',
      }),
    )

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.source).toBe('mock')
    expect(result.current.items.length).toBeGreaterThan(0)
  })
})
