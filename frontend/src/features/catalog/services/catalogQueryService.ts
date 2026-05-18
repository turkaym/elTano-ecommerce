import { getJson } from '../../../shared/api/httpClient'
import type { CatalogListItem, CatalogProduct, CatalogQueryResult, FeaturedProduct } from '../../../shared/types/catalog'
import { mockFeaturedProducts } from '../data/mockProducts'
import { normalizeCategorySlug } from '../utils/catalogQuery'
import { toFeaturedProduct } from './catalogProductMapper'

export interface CatalogItemsResponse {
  source: CatalogQueryResult['source']
  items: CatalogListItem[]
}

function toCatalogListItem(product: CatalogProduct): CatalogListItem | null {
  const featuredProduct = toFeaturedProduct(product)

  if (!featuredProduct) {
    return null
  }

  return {
    ...featuredProduct,
    categorySlug: product.categorySlug,
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
