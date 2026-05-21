import type { CartItem } from '../../../shared/types/checkout'

export const CART_STORAGE_KEY = 'eltano.cart.v1'

interface LoadCartResult {
  items: CartItem[]
  hadCorruptData: boolean
}

function isCartItem(value: unknown): value is CartItem {
  if (!value || typeof value !== 'object') {
    return false
  }

  const item = value as Record<string, unknown>
  const hasOptionalString = (key: string) => item[key] === undefined || typeof item[key] === 'string'
  const hasOptionalNullableString = (key: string) => (
    item[key] === undefined || item[key] === null || typeof item[key] === 'string'
  )

  return (
    typeof item.variantId === 'string' &&
    typeof item.productName === 'string' &&
    typeof item.unitLabel === 'string' &&
    typeof item.price === 'number' &&
    Number.isFinite(item.price) &&
    typeof item.quantity === 'number' &&
    Number.isInteger(item.quantity) &&
    item.quantity >= 1 &&
    typeof item.stockAvailable === 'number' &&
    Number.isInteger(item.stockAvailable) &&
    item.stockAvailable >= 0 &&
    hasOptionalString('productId') &&
    hasOptionalString('categoryName') &&
    hasOptionalString('imageUrl') &&
    hasOptionalNullableString('imageAltText')
  )
}

export function loadCart(): LoadCartResult {
  const raw = window.localStorage.getItem(CART_STORAGE_KEY)
  if (!raw) {
    return {
      items: [],
      hadCorruptData: false,
    }
  }

  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed) || !parsed.every(isCartItem)) {
      window.localStorage.removeItem(CART_STORAGE_KEY)
      return {
        items: [],
        hadCorruptData: true,
      }
    }

    return {
      items: parsed,
      hadCorruptData: false,
    }
  } catch {
    window.localStorage.removeItem(CART_STORAGE_KEY)
    return {
      items: [],
      hadCorruptData: true,
    }
  }
}

export function saveCart(items: CartItem[]) {
  window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items))
}

export function clearCartStorage() {
  window.localStorage.removeItem(CART_STORAGE_KEY)
}
