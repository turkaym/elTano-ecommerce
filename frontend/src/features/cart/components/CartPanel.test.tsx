import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { CartItem } from '../../../shared/types/checkout'
import { CartPanel } from './CartPanel'

const cartItem: CartItem = {
  variantId: 'variant-1',
  productName: 'Nuez mariposa',
  unitLabel: 'bolsa 500 g',
  price: 7600,
  quantity: 2,
  stockAvailable: 3,
  categoryName: 'Frutos secos',
  imageUrl: '/uploads/nuez.jpg',
  imageAltText: 'Nuez mariposa premium',
}

function renderCartPanel(items: CartItem[] = [cartItem]) {
  const handlers = {
    onDismissWarning: vi.fn(),
    onSetQty: vi.fn(),
    onRemove: vi.fn(),
    onClear: vi.fn(),
  }

  render(
    <CartPanel
      items={items}
      totals={{ itemCount: items.reduce((acc, item) => acc + item.quantity, 0), subtotal: 15200, total: 15200 }}
      warning={null}
      {...handlers}
    />,
  )

  return handlers
}

describe('CartPanel', () => {
  it('renders cart item cards with product media, category, presentation and subtotal', () => {
    renderCartPanel()

    const itemCard = screen.getByRole('listitem', { name: /Nuez mariposa/i })

    expect(within(itemCard).getByRole('img', { name: 'Nuez mariposa premium' })).toHaveAttribute(
      'src',
      '/uploads/nuez.jpg',
    )
    expect(within(itemCard).getByText('Frutos secos')).toBeInTheDocument()
    expect(within(itemCard).getByText('bolsa 500 g')).toBeInTheDocument()
    expect(within(itemCard).getByText(/\$\s*15\.200/)).toBeInTheDocument()
  })

  it('uses an accessible reserved media placeholder when legacy cart metadata has no image', () => {
    renderCartPanel([{ ...cartItem, imageUrl: undefined, imageAltText: null, categoryName: undefined }])

    const itemCard = screen.getByRole('listitem', { name: /Nuez mariposa/i })

    expect(within(itemCard).getByText('Imagen no disponible')).toBeInTheDocument()
    expect(within(itemCard).queryByRole('img')).not.toBeInTheDocument()
  })

  it('exposes plus, minus and remove controls with clamped quantity intents', async () => {
    const user = userEvent.setup()
    const handlers = renderCartPanel()

    await user.click(screen.getByRole('button', { name: 'Sumar Nuez mariposa' }))
    await user.click(screen.getByRole('button', { name: 'Restar Nuez mariposa' }))
    await user.click(screen.getByRole('button', { name: 'Quitar Nuez mariposa' }))

    expect(handlers.onSetQty).toHaveBeenNthCalledWith(1, 'variant-1', 3)
    expect(handlers.onSetQty).toHaveBeenNthCalledWith(2, 'variant-1', 1)
    expect(handlers.onRemove).toHaveBeenCalledWith('variant-1')
  })
})
