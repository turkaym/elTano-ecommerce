import { getJson } from '../../../shared/api/httpClient'
import type { CatalogListItem, CatalogProduct, CatalogQueryResult, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'
import { normalizeCategorySlug } from '../utils/catalogQuery'

export interface CatalogItemsResponse {
  source: CatalogQueryResult['source']
  items: CatalogListItem[]
}

function toCatalogListItem(product: CatalogProduct): CatalogListItem | null {
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
    categorySlug: product.categorySlug,
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

function toMockCatalogListItem(product: FeaturedProduct): CatalogListItem {
  return {
    ...product,
    categorySlug: normalizeCategorySlug(product.categoryName),
  }
}

function getMockCatalogItems(): CatalogListItem[] {
  return mockFeaturedProducts.map(toMockCatalogListItem)
}

export async function getCatalogListItems(): Promise<CatalogItemsResponse> {
  try {
    const products = await getJson<CatalogProduct[]>('/api/catalog/products')
    const items = products.map(toCatalogListItem).filter((item): item is CatalogListItem => item !== null)

    if (!items.length) {
      return {
        source: 'mock',
        items: getMockCatalogItems(),
      }
    }

    return {
      source: 'api',
      items,
    }
  } catch {
    return {
      source: 'mock',
      items: getMockCatalogItems(),
    }
  }
}
