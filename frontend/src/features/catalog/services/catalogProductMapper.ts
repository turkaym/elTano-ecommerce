import type { CatalogProduct, FeaturedProduct } from '../../../shared/types/catalog'

function resolveCatalogImageUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url
  }

  const apiUrl = import.meta.env.VITE_API_URL?.trim() ?? ''
  if (!apiUrl) {
    return url
  }

  const normalizedBase = apiUrl.endsWith('/') ? apiUrl.slice(0, -1) : apiUrl
  const normalizedPath = url.startsWith('/') ? url : `/${url}`
  return `${normalizedBase}${normalizedPath}`
}

function getPrimaryCatalogImage(product: CatalogProduct) {
  const images = product.images ?? []
  const primaryImage = images.find((image) => image.primary) ?? [...images].sort((left, right) => left.sortOrder - right.sortOrder)[0]

  if (!primaryImage) {
    return {
      primaryImageUrl: null,
      primaryImageAltText: null,
    }
  }

  return {
    primaryImageUrl: resolveCatalogImageUrl(primaryImage.url),
    primaryImageAltText: primaryImage.altText,
  }
}

export function toFeaturedProduct(product: CatalogProduct): FeaturedProduct | null {
  const variants = product.variants.map((variant) => ({
    id: variant.id,
    unitLabel: variant.unitLabel,
    price: Number(variant.price),
    stockAvailable: variant.stockAvailable,
    stockReserved: variant.stockReserved,
    weightGrams: variant.weightGrams,
  }))

  if (!variants.length) {
    return null
  }

  const minPrice = variants.reduce((lowest, variant) => Math.min(lowest, variant.price), variants[0].price)
  const defaultVariant = variants.find((variant) => variant.stockAvailable > 0) ?? variants[0]
  const stockAvailable = product.inventoryPolicy === 'BULK_WEIGHT'
    ? Math.max(0, product.stockAvailableBaseGrams ?? 0)
    : variants.reduce((total, variant) => total + variant.stockAvailable, 0)
  const isMultiVariant = variants.length > 1

  return {
    id: product.id,
    name: product.name,
    description: product.description,
    categoryName: product.categoryName,
    productType: product.productType,
    inventoryPolicy: product.inventoryPolicy,
    stockAvailableBaseGrams: product.stockAvailableBaseGrams,
    variants,
    isMultiVariant,
    minPrice,
    stockAvailable,
    variantId: isMultiVariant ? null : defaultVariant.id,
    unitLabel: isMultiVariant ? 'Seleccionar presentación' : defaultVariant.unitLabel,
    price: isMultiVariant ? minPrice : defaultVariant.price,
    ...getPrimaryCatalogImage(product),
  }
}
