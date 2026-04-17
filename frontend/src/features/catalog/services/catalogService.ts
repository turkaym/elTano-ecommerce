import { getJson } from '../../../shared/api/httpClient'
import type { CatalogProduct, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'

export interface FeaturedProductsResult {
  products: FeaturedProduct[]
  source: 'api' | 'mock'
}

function toFeaturedProduct(product: CatalogProduct): FeaturedProduct | null {
  const firstVariant = product.variants[0]
  if (!firstVariant) {
    return null
  }

  return {
    id: product.id,
    variantId: firstVariant.id,
    name: product.name,
    description: product.description,
    categoryName: product.categoryName,
    unitLabel: firstVariant.unitLabel,
    price: Number(firstVariant.price),
    stockAvailable: firstVariant.stockAvailable,
  }
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
