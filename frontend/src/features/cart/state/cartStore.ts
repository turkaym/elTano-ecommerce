import { useEffect, useMemo, useReducer } from 'react'
import type { CartItem, CartTotals } from '../../../shared/types/checkout'
import { clearCartStorage, loadCart, saveCart } from '../storage/cartStorage'

interface CartState {
  items: CartItem[]
  warning: string | null
}

type CartAction =
  | { type: 'addItem'; payload: CartItem }
  | { type: 'setQty'; payload: { variantId: string; quantity: number } }
  | { type: 'removeItem'; payload: { variantId: string } }
  | { type: 'clear' }
  | { type: 'dismissWarning' }

function initializeState(): CartState {
  const { items, hadCorruptData } = loadCart()
  return {
    items,
    warning: hadCorruptData
      ? 'Detectamos un carrito guardado invalido y lo reiniciamos. Podes volver a agregar productos.'
      : null,
  }
}

function cartReducer(state: CartState, action: CartAction): CartState {
  if (action.type === 'addItem') {
    const existing = state.items.find((item) => item.variantId === action.payload.variantId)
    if (!existing) {
      return {
        ...state,
        items: [
          ...state.items,
          {
            ...action.payload,
            quantity: Math.min(Math.max(1, action.payload.quantity), action.payload.stockAvailable),
          },
        ],
      }
    }

    return {
      ...state,
      items: state.items.map((item) => {
        if (item.variantId !== action.payload.variantId) {
          return item
        }

        return {
          ...item,
          quantity: Math.min(item.quantity + action.payload.quantity, item.stockAvailable),
        }
      }),
    }
  }

  if (action.type === 'setQty') {
    const nextQuantity = Math.max(1, action.payload.quantity)
    return {
      ...state,
      items: state.items.map((item) => {
        if (item.variantId !== action.payload.variantId) {
          return item
        }

        return {
          ...item,
          quantity: Math.min(nextQuantity, item.stockAvailable),
        }
      }),
    }
  }

  if (action.type === 'removeItem') {
    return {
      ...state,
      items: state.items.filter((item) => item.variantId !== action.payload.variantId),
    }
  }

  if (action.type === 'clear') {
    return {
      ...state,
      items: [],
    }
  }

  if (action.type === 'dismissWarning') {
    return {
      ...state,
      warning: null,
    }
  }

  return state
}

function calculateTotals(items: CartItem[]): CartTotals {
  const subtotal = items.reduce((acc, item) => acc + item.price * item.quantity, 0)
  const itemCount = items.reduce((acc, item) => acc + item.quantity, 0)
  return {
    itemCount,
    subtotal,
    total: subtotal,
  }
}

export function useCartStore() {
  const [state, dispatch] = useReducer(cartReducer, undefined, initializeState)

  useEffect(() => {
    if (!state.items.length) {
      clearCartStorage()
      return
    }

    saveCart(state.items)
  }, [state.items])

  const totals = useMemo(() => calculateTotals(state.items), [state.items])

  return {
    items: state.items,
    warning: state.warning,
    totals,
    addItem: (item: CartItem) => dispatch({ type: 'addItem', payload: item }),
    setQty: (variantId: string, quantity: number) =>
      dispatch({ type: 'setQty', payload: { variantId, quantity } }),
    removeItem: (variantId: string) => dispatch({ type: 'removeItem', payload: { variantId } }),
    clear: () => dispatch({ type: 'clear' }),
    dismissWarning: () => dispatch({ type: 'dismissWarning' }),
  }
}
