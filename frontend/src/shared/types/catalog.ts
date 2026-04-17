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
