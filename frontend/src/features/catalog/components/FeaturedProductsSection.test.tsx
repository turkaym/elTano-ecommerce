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
  primaryImageUrl: 'https://cdn.example.test/almendra.jpg',
  primaryImageAltText: 'Almendra tostada en bolsa',
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
  primaryImageUrl: null,
  primaryImageAltText: null,
  minPrice: 1200,
  isMultiVariant: true,
  variants: [
    { id: 'gr-100', unitLabel: '100g', price: 1200, stockAvailable: 3, stockReserved: 0, weightGrams: 100 },
    { id: 'gr-250', unitLabel: '250g', price: 3000, stockAvailable: 1, stockReserved: 0, weightGrams: 250 },
    { id: 'gr-500', unitLabel: '500g', price: 6000, stockAvailable: 0, stockReserved: 0, weightGrams: 500 },
  ],
}

describe('FeaturedProductsSection', () => {
  it('renders uploaded product images and accessible placeholders in the same card media area', () => {
    render(
      <FeaturedProductsSection
        products={[multiVariantProduct, granelProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.getByRole('img', { name: 'Almendra tostada en bolsa' })).toHaveAttribute(
      'src',
      'https://cdn.example.test/almendra.jpg',
    )
    expect(screen.getByLabelText('Imagen no disponible para Castañas de cajú')).toHaveTextContent(
      'Sin imagen',
    )
  })

  it('keeps product card actions in a dedicated alignment region after the reserved media area', () => {
    const { container } = render(
      <FeaturedProductsSection
        products={[multiVariantProduct, granelProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    const cards = Array.from(container.querySelectorAll('.product-card'))
    expect(cards).toHaveLength(2)

    for (const card of cards) {
      expect(card.querySelector('.product-media')).toBeInTheDocument()
      expect(card.querySelector('.product-card-actions')).toBeInTheDocument()
      expect(card.querySelector('.product-card-actions .btn')).toBeInTheDocument()
    }
  })

  it('shows the selected presentation price instead of "Desde" for multi-variant product cards', async () => {
    const user = userEvent.setup()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.queryByText(/Desde/i)).not.toBeInTheDocument()
    expect(screen.getByText(/\$\s*7.600/i)).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Presentacion para Almendra premium'), 'var-250')

    expect(screen.getByText(/\$\s*4.200/i)).toBeInTheDocument()
  })

  it('adds the default reference presentation when the quantity input is untouched', async () => {
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

    expect(onAddToCart).toHaveBeenCalledWith({
      productId: 'prod-1',
      variantId: 'var-500',
      quantity: 1,
    })
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

  it('increments quantity with an accessible plus control before adding to cart', async () => {
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
    await user.click(screen.getByRole('button', { name: 'Aumentar cantidad para Almendra premium' }))
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onAddToCart).toHaveBeenCalledWith({
      productId: 'prod-1',
      variantId: 'var-500',
      quantity: 2,
    })
  })

  it('disables quantity controls at the selected presentation limits', async () => {
    const user = userEvent.setup()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    await user.selectOptions(screen.getByLabelText('Presentacion para Almendra premium'), 'var-500')

    const minusButton = screen.getByRole('button', { name: 'Reducir cantidad para Almendra premium' })
    const plusButton = screen.getByRole('button', { name: 'Aumentar cantidad para Almendra premium' })

    expect(minusButton).toBeDisabled()
    expect(plusButton).not.toBeDisabled()

    await user.click(plusButton)
    await user.click(plusButton)
    await user.click(plusButton)

    expect(screen.getByLabelText('Cantidad para Almendra premium')).toHaveValue(4)
    expect(minusButton).not.toBeDisabled()
    expect(plusButton).toBeDisabled()
  })

  it('shows accessible added feedback after adding a product', async () => {
    const user = userEvent.setup()

    render(
      <FeaturedProductsSection
        products={[multiVariantProduct]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    await user.selectOptions(screen.getByLabelText('Presentacion para Almendra premium'), 'var-250')
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(screen.getByRole('button', { name: 'Agregado' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Producto agregado')
  })

  it('keeps feedback available when adding the same product repeatedly', async () => {
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
    await user.click(screen.getByRole('button', { name: 'Agregado' }))

    expect(onAddToCart).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('status')).toHaveTextContent('Producto agregado')
  })

  it('clamps quantity to the selected presentation stock before adding to cart', async () => {
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
      target: { value: '9' },
    })
    await user.click(screen.getByRole('button', { name: 'Agregar' }))

    expect(onAddToCart).toHaveBeenCalledWith({
      productId: 'prod-1',
      variantId: 'var-500',
      quantity: 4,
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

  it('defaults bulk-weight products to the 1kg presentation when available', () => {
    render(
      <FeaturedProductsSection
        products={[{
          ...granelProduct,
          stockAvailableBaseGrams: 2_000,
          stockAvailable: 2_000,
          variants: [
            { id: 'gr-100', unitLabel: '100g', price: 1200, stockAvailable: 20, stockReserved: 0, weightGrams: 100 },
            { id: 'gr-500', unitLabel: '500g', price: 6000, stockAvailable: 4, stockReserved: 0, weightGrams: 500 },
            { id: 'gr-1kg', unitLabel: '1kg', price: 12000, stockAvailable: 2, stockReserved: 0, weightGrams: 1000 },
          ],
        }]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Presentacion para Castañas de cajú')).toHaveDisplayValue('1kg')
    expect(screen.getByText(/\$\s*12.000/i)).toBeInTheDocument()
  })

  it('defaults liquid products to the 1L presentation when available', () => {
    render(
      <FeaturedProductsSection
        products={[{
          ...multiVariantProduct,
          name: 'Aceite de oliva',
          categoryName: 'Aceites',
          productType: 'UNIDAD',
          variants: [
            { id: 'oil-500', unitLabel: '500 ml', price: 7200, stockAvailable: 5, stockReserved: 0 },
            { id: 'oil-1l', unitLabel: '1L', price: 13500, stockAvailable: 3, stockReserved: 0 },
          ],
        }]}
        isLoading={false}
        source="api"
        onAddToCart={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('Presentacion para Aceite de oliva')).toHaveDisplayValue('1L')
    expect(screen.getByText(/\$\s*13.500/i)).toBeInTheDocument()
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
    expect(presentation).toHaveDisplayValue('250g')
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
