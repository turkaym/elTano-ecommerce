export interface CatalogVariant {
  id: string
  sku: string
  unitType: string
  weightGrams: number | null
  unitLabel: string
  price: number
  stockAvailable: number
  stockReserved: number
  attributesJson: string | null
}

export interface CatalogProduct {
  id: string
  name: string
  slug: string
  description: string
  categoryName: string
  categorySlug: string
  variants: CatalogVariant[]
}

export interface FeaturedProduct {
  id: string
  variantId: string
  name: string
  description: string
  categoryName: string
  unitLabel: string
  price: number
  stockAvailable: number
}

export type CatalogSort = 'name-asc' | 'name-desc' | 'price-asc' | 'price-desc'

export type CatalogStockFilter = 'all' | 'in-stock'

export interface CatalogQueryState {
  q: string
  category: string | null
  stock: CatalogStockFilter
  sort: CatalogSort
}

export interface CatalogListItem extends FeaturedProduct {
  categorySlug: string
}

export interface CatalogCategorySummary {
  name: string
  slug: string
  count: number
}

export interface CatalogQueryResult {
  source: 'api' | 'mock'
  isLoading: boolean
  items: CatalogListItem[]
  categories: CatalogCategorySummary[]
}
