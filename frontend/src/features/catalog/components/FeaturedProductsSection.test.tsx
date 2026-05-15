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
      stockReserved: 0,
      weightGrams: 250,
    },
    {
      id: 'var-500',
      unitLabel: 'bolsa 500 g',
      price: 7600,
      stockAvailable: 4,
      stockReserved: 0,
      weightGrams: 500,
    },
  ],
}

const granelProduct: FeaturedProduct = {
  id: 'prod-granel',
  name: 'Castañas de cajú',
  description: 'A granel',
  categoryName: 'Frutos secos',
  productType: 'GRANEL',
  inventoryPolicy: 'BULK_WEIGHT',
  stockAvailableBaseGrams: 300,
  variantId: '',
  unitLabel: 'Seleccionar presentación',
  price: 1200,
  stockAvailable: 3,
  minPrice: 1200,
  isMultiVariant: true,
  variants: [
    { id: 'gr-100', unitLabel: '100g', price: 1200, stockAvailable: 3, stockReserved: 0, weightGrams: 100 },
    { id: 'gr-250', unitLabel: '250g', price: 3000, stockAvailable: 1, stockReserved: 0, weightGrams: 250 },
    { id: 'gr-500', unitLabel: '500g', price: 6000, stockAvailable: 0, stockReserved: 0, weightGrams: 500 },
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

  it('disables bulk-weight presentations that exceed shared available grams', () => {
    render(
      <FeaturedProductsSection
        products={[granelProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    const presentation = screen.getByLabelText('Presentacion para Castañas de cajú')
    expect(presentation).toHaveDisplayValue('Seleccionar')
    expect(screen.getByRole('option', { name: '100g' })).not.toBeDisabled()
    expect(screen.getByRole('option', { name: '250g' })).not.toBeDisabled()
    expect(screen.getByRole('option', { name: '500g - sin stock' })).toBeDisabled()
  })

  it('marks bulk-weight products out of stock when less than the 100g minimum is available', () => {
    render(
      <FeaturedProductsSection
        products={[{ ...granelProduct, stockAvailableBaseGrams: 80, stockAvailable: 0, variants: granelProduct.variants.map((variant) => ({ ...variant, stockAvailable: 0 })) }]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: 'Sin stock' })).toBeDisabled()
    expect(screen.getByText('Sin stock para esta presentación.')).toBeInTheDocument()
  })
})
