import { beforeEach, describe, expect, it, vi } from 'vitest'
import { applyCatalogQuery } from '../utils/catalogQuery'
import { getCatalogListItems } from './catalogQueryService'

vi.mock('../../../shared/api/httpClient', () => ({
  getJson: vi.fn(),
}))

import { getJson } from '../../../shared/api/httpClient'

describe('catalogQueryService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllEnvs()
  })

  it('builds variant-ready list items with min price label metadata', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-1',
        name: 'Nuez',
        slug: 'nuez',
        description: 'Premium',
        categoryName: 'Frutos secos',
        categorySlug: 'frutos-secos',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        variants: [
          {
            id: 'var-a',
            sku: 'N-250',
            unitType: 'BAG',
            weightGrams: 250,
            unitLabel: 'bolsa 250 g',
            price: 4300,
            stockAvailable: 3,
            stockReserved: 0,
            attributesJson: null,
          },
          {
            id: 'var-b',
            sku: 'N-500',
            unitType: 'BAG',
            weightGrams: 500,
            unitLabel: 'bolsa 500 g',
            price: 8100,
            stockAvailable: 6,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getCatalogListItems()

    expect(result.source).toBe('api')
    expect(result.items[0]).toMatchObject({
      isMultiVariant: true,
      minPrice: 4300,
    })
    expect(result.items[0]?.variants).toHaveLength(2)
  })

  it('keeps selected variant metadata for single-variant products', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-2',
        name: 'Aceite de oliva',
        slug: 'aceite-de-oliva',
        description: 'Extra virgen',
        categoryName: 'Aceites',
        categorySlug: 'aceites',
        productType: 'UNIDAD',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        variants: [
          {
            id: 'var-single',
            sku: 'A-500',
            unitType: 'BOTTLE',
            weightGrams: null,
            unitLabel: 'botella 500 ml',
            price: 7200,
            stockAvailable: 5,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getCatalogListItems()

    expect(result.items[0]).toMatchObject({
      isMultiVariant: false,
      minPrice: 7200,
      variantId: 'var-single',
      unitLabel: 'botella 500 ml',
    })
  })

  it('maps image metadata and keeps category text searchable', async () => {
    vi.stubEnv('VITE_API_URL', 'https://api.eltano.test')
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-3',
        name: 'Garbanzo',
        slug: 'garbanzo',
        description: 'Seco',
        categoryName: 'Legumbres',
        categorySlug: 'legumbres',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        images: [
          { id: 'img-1', url: '/uploads/garbanzo.jpg', altText: 'Garbanzo en bolsa', sortOrder: 1, primary: true },
        ],
        variants: [
          {
            id: 'var-g',
            sku: 'G-1',
            unitType: 'BAG',
            weightGrams: 500,
            unitLabel: 'bolsa 500 g',
            price: 2800,
            stockAvailable: 12,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
      {
        id: 'prod-4',
        name: 'Almendra',
        slug: 'almendra',
        description: 'Natural',
        categoryName: 'Frutos secos',
        categorySlug: 'frutos-secos',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        variants: [
          {
            id: 'var-a',
            sku: 'A-1',
            unitType: 'BAG',
            weightGrams: 250,
            unitLabel: 'bolsa 250 g',
            price: 4300,
            stockAvailable: 3,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getCatalogListItems()
    const filtered = applyCatalogQuery(result.items, { q: 'legumbres', category: null, stock: 'all', sort: 'name-asc' })

    expect(filtered).toHaveLength(1)
    expect(filtered[0]).toMatchObject({
      name: 'Garbanzo',
      primaryImageUrl: 'https://api.eltano.test/uploads/garbanzo.jpg',
      primaryImageAltText: 'Garbanzo en bolsa',
    })
  })
})
