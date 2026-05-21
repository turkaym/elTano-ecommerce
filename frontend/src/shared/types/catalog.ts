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

export interface CatalogProductImage {
  id: string
  url: string
  altText: string | null
  sortOrder: number
  primary: boolean
}

export type ProductType = 'GRANEL' | 'ENVASADO' | 'UNIDAD'
export type InventoryPolicy = 'BULK_WEIGHT' | 'PER_VARIANT'

export interface CatalogCardVariantOption {
  id: string
  unitLabel: string
  price: number
  stockAvailable: number
  stockReserved?: number
  weightGrams?: number | null
}

export interface CatalogProduct {
  id: string
  name: string
  slug: string
  description: string
  categoryName: string
  categorySlug: string
  productType: ProductType
  inventoryPolicy: InventoryPolicy
  stockBaseGrams: number | null
  stockReservedBaseGrams?: number | null
  stockAvailableBaseGrams?: number | null
  images?: CatalogProductImage[]
  variants: CatalogVariant[]
}

export interface FeaturedProduct {
  id: string
  name: string
  description: string
  categoryName: string
  productType: ProductType
  inventoryPolicy: InventoryPolicy
  stockAvailableBaseGrams?: number | null
  variants: CatalogCardVariantOption[]
  isMultiVariant: boolean
  minPrice: number
  stockAvailable: number
  variantId: string | null
  unitLabel: string
  price: number
  primaryImageUrl?: string | null
  primaryImageAltText?: string | null
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
