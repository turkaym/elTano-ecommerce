import { getJson } from '../../../shared/api/httpClient'
import type { CatalogProduct, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'

export interface FeaturedProductsResult {
  products: FeaturedProduct[]
  source: 'api' | 'mock'
}

function toFeaturedProduct(product: CatalogProduct): FeaturedProduct | null {
  const variants = product.variants.map((variant) => ({
    id: variant.id,
    unitLabel: variant.unitLabel,
    price: Number(variant.price),
    stockAvailable: variant.stockAvailable,
  }))

  if (!variants.length) {
    return null
  }

  const minPrice = variants.reduce((lowest, variant) => Math.min(lowest, variant.price), variants[0].price)
  const defaultVariant = variants.find((variant) => variant.stockAvailable > 0) ?? variants[0]
  const stockAvailable = variants.reduce((total, variant) => total + variant.stockAvailable, 0)
  const isMultiVariant = variants.length > 1

  return {
    id: product.id,
    name: product.name,
    description: product.description,
    categoryName: product.categoryName,
    productType: product.productType,
    inventoryPolicy: product.inventoryPolicy,
    variants,
    isMultiVariant,
    minPrice,
    stockAvailable,
    variantId: isMultiVariant ? null : defaultVariant.id,
    unitLabel: isMultiVariant ? 'Seleccionar presentación' : defaultVariant.unitLabel,
    price: isMultiVariant ? minPrice : defaultVariant.price,
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
