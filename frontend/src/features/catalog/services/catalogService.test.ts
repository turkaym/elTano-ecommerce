import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getFeaturedProducts } from './catalogService'

vi.mock('../../../shared/api/httpClient', () => ({
  getJson: vi.fn(),
}))

import { getJson } from '../../../shared/api/httpClient'

describe('catalogService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.unstubAllEnvs()
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

  it('maps the primary uploaded image from dashboard payloads', async () => {
    vi.stubEnv('VITE_API_URL', 'https://api.eltano.test/')
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-3',
        name: 'Pistacho',
        slug: 'pistacho',
        description: 'Tostado',
        categoryName: 'Frutos secos',
        categorySlug: 'frutos-secos',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        images: [
          { id: 'img-secondary', url: '/uploads/pistacho-back.jpg', altText: 'Pistacho detalle', sortOrder: 2, primary: false },
          { id: 'img-primary', url: '/uploads/pistacho-front.jpg', altText: 'Pistacho principal', sortOrder: 1, primary: true },
        ],
        variants: [
          {
            id: 'var-3',
            sku: 'P-1',
            unitType: 'BAG',
            weightGrams: 250,
            unitLabel: 'bolsa 250 g',
            price: 6900,
            stockAvailable: 5,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getFeaturedProducts(6)

    expect(result.products[0]).toMatchObject({
      primaryImageUrl: 'https://api.eltano.test/uploads/pistacho-front.jpg',
      primaryImageAltText: 'Pistacho principal',
    })
  })

  it('keeps placeholder image metadata when a product has no uploaded image', async () => {
    vi.mocked(getJson).mockResolvedValueOnce([
      {
        id: 'prod-4',
        name: 'Quinoa',
        slug: 'quinoa',
        description: 'Organica',
        categoryName: 'Cereales',
        categorySlug: 'cereales',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        stockBaseGrams: null,
        images: [],
        variants: [
          {
            id: 'var-4',
            sku: 'Q-1',
            unitType: 'BAG',
            weightGrams: 500,
            unitLabel: 'bolsa 500 g',
            price: 4100,
            stockAvailable: 7,
            stockReserved: 0,
            attributesJson: null,
          },
        ],
      },
    ])

    const result = await getFeaturedProducts(6)

    expect(result.products[0]).toMatchObject({
      primaryImageUrl: null,
      primaryImageAltText: null,
    })
  })
})
