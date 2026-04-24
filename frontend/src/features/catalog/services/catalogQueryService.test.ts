import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCatalogListItems } from './catalogQueryService'

vi.mock('../../../shared/api/httpClient', () => ({
  getJson: vi.fn(),
}))

import { getJson } from '../../../shared/api/httpClient'

describe('catalogQueryService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
})
