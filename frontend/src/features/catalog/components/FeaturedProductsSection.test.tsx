import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { FeaturedProduct } from '../../../shared/types/catalog'
import { FeaturedProductsSection } from './FeaturedProductsSection'

const multiVariantProduct: FeaturedProduct = {
  id: 'prod-1',
  name: 'Almendra premium',
  description: 'Snack',
  categoryName: 'Frutos secos',
  productType: 'ENVASADO',
  inventoryPolicy: 'PER_VARIANT',
  variantId: '',
  unitLabel: 'Seleccionar variante',
  price: 0,
  stockAvailable: 10,
  minPrice: 4200,
  isMultiVariant: true,
  variants: [
    {
      id: 'var-250',
      unitLabel: 'bolsa 250 g',
      price: 4200,
      stockAvailable: 8,
    },
    {
      id: 'var-500',
      unitLabel: 'bolsa 500 g',
      price: 7600,
      stockAvailable: 4,
    },
  ],
}

describe('FeaturedProductsSection', () => {
  it('shows "Desde" price label for multi-variant product cards', () => {
    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.getByText(/Desde\s*\$\s*4.200/i)).toBeInTheDocument()
  })

  it('requires selecting a variant before adding to cart', async () => {
    const user = userEvent.setup()
    const onAddToCart = vi.fn()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={onAddToCart}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onAddToCart).not.toHaveBeenCalled()
    expect(screen.getByText('Selecciona una variante para continuar.')).toBeInTheDocument()
  })

  it('sends selected variant and quantity when adding to cart', async () => {
    const user = userEvent.setup()
    const onAddToCart = vi.fn()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={onAddToCart}
      />,
    )

    await user.selectOptions(screen.getByLabelText('Presentacion para Almendra premium'), 'var-500')
    fireEvent.change(screen.getByLabelText('Cantidad para Almendra premium'), {
      target: { value: '3' },
    })
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onAddToCart).toHaveBeenCalledWith({
      productId: 'prod-1',
      variantId: 'var-500',
      quantity: 3,
    })
  })

  it('defaults variant-first add-to-cart quantity to 1 when input is untouched', async () => {
    const user = userEvent.setup()
    const onAddToCart = vi.fn()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={onAddToCart}
      />,
    )

    await user.selectOptions(screen.getByLabelText('Presentacion para Almendra premium'), 'var-250')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onAddToCart).toHaveBeenCalledWith({
      productId: 'prod-1',
      variantId: 'var-250',
      quantity: 1,
    })
  })
})
