import type { AdminProductDto, AdminProductVariantDto } from '../services/adminOperationsService'

export const LOW_STOCK_UNITS_THRESHOLD = 5
export const LOW_STOCK_GRAMS_THRESHOLD = 5_000

export type AdminStockAttention = 'no-stock' | 'low-stock' | 'available'

export interface AdminStockState {
  state: AdminStockAttention
  badgeLabel: string
  summaryLabel: string
  stockBaseGrams?: number
  stockReservedBaseGrams?: number
  stockAvailableBaseGrams?: number
  variant?: AdminProductVariantDto
}

export function getAdminStockState(product: AdminProductDto): AdminStockState {
  if (isBulkWeightProduct(product)) {
    const stockBaseGrams = normalizeStock(product.stockBaseGrams)
    const stockReservedBaseGrams = normalizeStock(product.stockReservedBaseGrams)
    const stockAvailableBaseGrams = Math.max(0, stockBaseGrams - stockReservedBaseGrams)
    const state = getBaseGramAttention(stockAvailableBaseGrams)

    return {
      state,
      badgeLabel: getBadgeLabel(state),
      summaryLabel: `${formatAdminWeight(stockAvailableBaseGrams)} disponibles · ${formatAdminWeight(stockReservedBaseGrams)} reservados`,
      stockBaseGrams,
      stockReservedBaseGrams,
      stockAvailableBaseGrams,
    }
  }

  const variants = product.variants ?? []
  const outVariant = variants.find((variant) => normalizeStock(variant.stockAvailable) <= 0)
  if (outVariant) {
    return buildVariantState('no-stock', outVariant, `${variantLabel(outVariant)} · Sin stock`)
  }

  const lowVariant = variants.find((variant) => normalizeStock(variant.stockAvailable) <= LOW_STOCK_UNITS_THRESHOLD)
  if (lowVariant) {
    return buildVariantState('low-stock', lowVariant, `${variantLabel(lowVariant)} · ${normalizeStock(lowVariant.stockAvailable)} disponibles`)
  }

  return {
    state: 'available',
    badgeLabel: getBadgeLabel('available'),
    summaryLabel: variants.length ? 'Variantes disponibles' : 'Sin variantes cargadas',
  }
}

export function formatAdminWeight(grams: number): string {
  const normalizedGrams = normalizeStock(grams)
  if (normalizedGrams >= 1000) {
    return `${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(normalizedGrams / 1000)} kg`
  }
  return `${new Intl.NumberFormat('es-AR').format(normalizedGrams)} g`
}

function isBulkWeightProduct(product: AdminProductDto): boolean {
  return product.inventoryPolicy === 'BULK_WEIGHT' || product.productType === 'GRANEL'
}

function getBaseGramAttention(availableGrams: number): AdminStockAttention {
  if (availableGrams <= 0) return 'no-stock'
  if (availableGrams <= LOW_STOCK_GRAMS_THRESHOLD) return 'low-stock'
  return 'available'
}

function buildVariantState(state: Exclude<AdminStockAttention, 'available'>, variant: AdminProductVariantDto, summaryLabel: string): AdminStockState {
  return {
    state,
    badgeLabel: getBadgeLabel(state),
    summaryLabel,
    variant,
  }
}

function getBadgeLabel(state: AdminStockAttention): string {
  if (state === 'no-stock') return 'Sin stock'
  if (state === 'low-stock') return 'Stock bajo'
  return 'Disponible'
}

function normalizeStock(value: number | null | undefined): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return 0
  return Math.max(0, value)
}

function variantLabel(variant: AdminProductVariantDto): string {
  return variant.unitLabel || 'Variante'
}
