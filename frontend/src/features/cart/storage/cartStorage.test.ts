import { describe, expect, it } from 'vitest'
import type { CartItem } from '../../../shared/types/checkout'
import { CART_STORAGE_KEY, loadCart, saveCart } from './cartStorage'

const sampleCart: CartItem[] = [
  {
    variantId: 'variant-1',
    productName: 'Almendras',
    unitLabel: 'bolsa 500 g',
    price: 6400,
    quantity: 2,
    stockAvailable: 10,
  },
]

describe('cartStorage', () => {
  it('persists and restores cart items', () => {
    saveCart(sampleCart)

    const restored = loadCart()

    expect(restored.items).toEqual(sampleCart)
    expect(restored.hadCorruptData).toBe(false)
  })

  it('recovers to empty cart when JSON payload is corrupt', () => {
    window.localStorage.setItem(CART_STORAGE_KEY, '{bad-json')

    const restored = loadCart()

    expect(restored.items).toEqual([])
    expect(restored.hadCorruptData).toBe(true)
    expect(window.localStorage.getItem(CART_STORAGE_KEY)).toBeNull()
  })

  it('recovers to empty cart when payload shape is invalid', () => {
    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify([{ id: 'invalid' }]))

    const restored = loadCart()

    expect(restored.items).toEqual([])
    expect(restored.hadCorruptData).toBe(true)
    expect(window.localStorage.getItem(CART_STORAGE_KEY)).toBeNull()
  })
})
