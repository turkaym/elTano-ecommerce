import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CART_STORAGE_KEY } from '../storage/cartStorage'
import { useCartStore } from './cartStore'

const baseItem = {
  variantId: 'variant-1',
  productName: 'Nuez mariposa',
  unitLabel: 'bolsa 500 g',
  price: 7600,
  quantity: 1,
  stockAvailable: 2,
}

describe('useCartStore', () => {
  it('handles add/update/remove actions and keeps totals consistent', () => {
    const { result } = renderHook(() => useCartStore())

    act(() => {
      result.current.addItem(baseItem)
      result.current.addItem(baseItem)
      result.current.addItem(baseItem)
    })

    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0]?.quantity).toBe(2)
    expect(result.current.totals.itemCount).toBe(2)
    expect(result.current.totals.subtotal).toBe(15200)

    act(() => {
      result.current.setQty(baseItem.variantId, 0)
    })

    expect(result.current.items[0]?.quantity).toBe(1)
    expect(result.current.totals.total).toBe(7600)

    act(() => {
      result.current.removeItem(baseItem.variantId)
    })

    expect(result.current.items).toEqual([])
    expect(result.current.totals.itemCount).toBe(0)
    expect(result.current.totals.total).toBe(0)
  })

  it('hydrates a warning when stored cart payload is corrupt', () => {
    window.localStorage.setItem(CART_STORAGE_KEY, '{not-json')

    const { result } = renderHook(() => useCartStore())

    expect(result.current.items).toEqual([])
    expect(result.current.warning).toContain('carrito guardado invalido')

    act(() => {
      result.current.dismissWarning()
    })

    expect(result.current.warning).toBeNull()
  })
})
