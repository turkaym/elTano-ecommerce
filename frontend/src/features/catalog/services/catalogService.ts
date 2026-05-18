import { getJson } from '../../../shared/api/httpClient'
import type { CatalogProduct, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'
import { toFeaturedProduct } from './catalogProductMapper'

export interface FeaturedProductsResult {
  products: FeaturedProduct[]
  source: 'api' | 'mock'
}

export async function getFeaturedProducts(
  limit = 6,
): Promise<FeaturedProductsResult> {
  try {
    const products = await getJson<CatalogProduct[]>('/api/catalog/products')
    const featuredProducts = products
      .map(toFeaturedProduct)
      .filter((item): item is FeaturedProduct => item !== null)
      .slice(0, limit)

    if (!featuredProducts.length) {
      return {
        products: mockFeaturedProducts.slice(0, limit),
        source: 'mock',
      }
    }

    return {
      products: featuredProducts,
      source: 'api',
    }
  } catch {
    return {
      products: mockFeaturedProducts.slice(0, limit),
      source: 'mock',
    }
  }
}
