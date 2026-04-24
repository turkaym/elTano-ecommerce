import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getFeaturedProducts } from './catalogService'

vi.mock('../../../shared/api/httpClient', () => ({
  getJson: vi.fn(),
}))

import { getJson } from '../../../shared/api/httpClient'

describe('catalogService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('computes Desde price for multi-variant products', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-1',
        name: 'Almendra',
        slug: 'almendra',
        description: 'Snack',
        categoryName: 'Frutos secos',
        categorySlug: 'frutos-secos',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        variants: [
          {
            id: 'var-1',
            sku: 'A-1',
            unitType: 'BAG',
            weightGrams: 250,
            unitLabel: 'bolsa 250 g',
            price: 4200,
            stockAvailable: 8,
            stockReserved: 0,
            attributesJson: null,
          },
          {
            id: 'var-2',
            sku: 'A-2',
            unitType: 'BAG',
            weightGrams: 500,
            unitLabel: 'bolsa 500 g',
            price: 7600,
            stockAvailable: 4,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getFeaturedProducts(6)

    expect(result.source).toBe('api')
    expect(result.products[0]).toMatchObject({
      minPrice: 4200,
      isMultiVariant: true,
    })
  })

  it('keeps direct price label metadata for single-variant products', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-2',
        name: 'Harina de coco',
        slug: 'harina-de-coco',
        description: 'Reposteria',
        categoryName: 'Harinas',
        categorySlug: 'harinas',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        variants: [
          {
            id: 'var-1',
            sku: 'H-1',
            unitType: 'BAG',
            weightGrams: 500,
            unitLabel: 'bolsa 500 g',
            price: 3900,
            stockAvailable: 10,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getFeaturedProducts(6)

    expect(result.products[0]).toMatchObject({
      minPrice: 3900,
      price: 3900,
      isMultiVariant: false,
      variantId: 'var-1',
    })
  })
})
