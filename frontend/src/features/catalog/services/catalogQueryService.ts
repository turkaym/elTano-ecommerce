import { getJson } from '../../../shared/api/httpClient'
import type { CatalogListItem, CatalogProduct, CatalogQueryResult, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'
import { normalizeCategorySlug } from '../utils/catalogQuery'

export interface CatalogItemsResponse {
  source: CatalogQueryResult['source']
  items: CatalogListItem[]
}

function toCatalogListItem(product: CatalogProduct): CatalogListItem | null {
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
    categorySlug: product.categorySlug,
    unitLabel: firstVariant.unitLabel,
    price: Number(firstVariant.price),
    stockAvailable: firstVariant.stockAvailable,
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
