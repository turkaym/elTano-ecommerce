import { describe, expect, it } from 'vitest'
import { getAdminStockState, formatAdminWeight } from './adminStockState'
import type { AdminProductDto } from '../services/adminOperationsService'

function product(overrides: Partial<AdminProductDto>): AdminProductDto {
  return {
    id: 'product-1',
    name: 'Test product',
    ...overrides,
  }
}

describe('getAdminStockState', () => {
  it('classifies GRANEL stock with reserved grams and readable available/reserved labels', () => {
    const state = getAdminStockState(product({
      productType: 'GRANEL',
      inventoryPolicy: 'BULK_WEIGHT',
      stockBaseGrams: 5_000,
      stockReservedBaseGrams: 500,
      variants: [{ unitLabel: '100g', stockAvailable: 0 }],
    }))

    expect(state).toMatchObject({
      state: 'low-stock',
      badgeLabel: 'Stock bajo',
      summaryLabel: '4,5 kg disponibles · 500 g reservados',
      stockBaseGrams: 5_000,
      stockReservedBaseGrams: 500,
      stockAvailableBaseGrams: 4_500,
    })
  })

  it('treats missing GRANEL reserved stock as zero for availability classification', () => {
    const state = getAdminStockState(product({
      productType: 'GRANEL',
      stockBaseGrams: 6_000,
    }))

    expect(state).toMatchObject({
      state: 'available',
      badgeLabel: 'Disponible',
      summaryLabel: '6 kg disponibles · 0 g reservados',
      stockReservedBaseGrams: 0,
      stockAvailableBaseGrams: 6_000,
    })
  })

  it('ignores generated GRANEL variant unit stock when base grams are available', () => {
    const state = getAdminStockState(product({
      productType: 'GRANEL',
      stockBaseGrams: 10_000,
      stockReservedBaseGrams: 0,
      variants: [
        { unitLabel: '100g', stockAvailable: 0 },
        { unitLabel: '250g', stockAvailable: 0 },
      ],
    }))

    expect(state.state).toBe('available')
    expect(state.summaryLabel).toBe('10 kg disponibles · 0 g reservados')
    expect(state.variant).toBeUndefined()
  })

  it('classifies GRANEL as no-stock when all base grams are reserved', () => {
    const state = getAdminStockState(product({
      inventoryPolicy: 'BULK_WEIGHT',
      stockBaseGrams: 5_000,
      stockReservedBaseGrams: 5_000,
    }))

    expect(state).toMatchObject({
      state: 'no-stock',
      badgeLabel: 'Sin stock',
      summaryLabel: '0 g disponibles · 5 kg reservados',
      stockAvailableBaseGrams: 0,
    })
  })

  it('uses per-variant unit availability for variant-managed products', () => {
    const state = getAdminStockState(product({
      productType: 'ENVASADO',
      inventoryPolicy: 'PER_VARIANT',
      variants: [
        { id: 'variant-1', unitLabel: '500g', stockAvailable: 8 },
        { id: 'variant-2', unitLabel: '1kg', stockAvailable: 3 },
      ],
    }))

    expect(state).toMatchObject({
      state: 'low-stock',
      badgeLabel: 'Stock bajo',
      summaryLabel: '1kg · 3 disponibles',
      variant: { id: 'variant-2', unitLabel: '1kg', stockAvailable: 3 },
    })
  })

  it('reports the first empty variant as no-stock for variant-managed products', () => {
    const state = getAdminStockState(product({
      productType: 'UNIDAD',
      variants: [
        { id: 'variant-1', unitLabel: 'Unidad', stockAvailable: 4 },
        { id: 'variant-2', unitLabel: 'Caja', stockAvailable: 0 },
      ],
    }))

    expect(state).toMatchObject({
      state: 'no-stock',
      badgeLabel: 'Sin stock',
      summaryLabel: 'Caja · Sin stock',
      variant: { id: 'variant-2', unitLabel: 'Caja', stockAvailable: 0 },
    })
  })
})

describe('formatAdminWeight', () => {
  it('formats grams and kilograms for admin stock readability', () => {
    expect(formatAdminWeight(500)).toBe('500 g')
    expect(formatAdminWeight(4_500)).toBe('4,5 kg')
  })
})
