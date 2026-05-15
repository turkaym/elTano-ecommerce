import { useState } from 'react'
import type { FeaturedProduct } from '../../../shared/types/catalog'

export interface FeaturedAddToCartPayload {
  productId: string
  variantId: string
  quantity: number
}

interface FeaturedProductsSectionProps {
  products: FeaturedProduct[]
  isLoading: boolean
  source: 'api' | 'mock'
  onAddToCart: (payload: FeaturedAddToCartPayload) => void
}

const currencyFormatter = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

export function FeaturedProductsSection({
  products,
  isLoading,
  source,
  onAddToCart,
}: FeaturedProductsSectionProps) {
  return (
    <section className="section" aria-labelledby="productos-destacados-title">
      <div className="section-header">
        <h2 id="productos-destacados-title">Productos destacados</h2>
        <p>Lo que mas sale hoy en El Tano.</p>
      </div>

      {source === 'mock' && !isLoading ? (
        <p className="products-hint" role="status">
          Mostrando seleccion local mientras conecta con el catalogo.
        </p>
      ) : null}

      {isLoading ? (
        <div className="catalog-empty catalog-empty-loading" role="status">
          <p className="catalog-empty-title">Cargando catálogo...</p>
          <p>Estamos preparando los productos destacados.</p>
        </div>
      ) : (
        <div className="products-grid">
          {products.map((product) => (
            <FeaturedProductCard key={product.id} product={product} onAddToCart={onAddToCart} />
          ))}
        </div>
      )}
    </section>
  )
}

interface FeaturedProductCardProps {
  product: FeaturedProduct
  onAddToCart: (payload: FeaturedAddToCartPayload) => void
}

export function FeaturedProductCard({ product, onAddToCart }: FeaturedProductCardProps) {
  const [selectedVariantId, setSelectedVariantId] = useState<string>(product.variantId ?? '')
  const [quantity, setQuantity] = useState(1)
  const [error, setError] = useState<string | null>(null)
  const selectedVariant = product.variants.find((variant) => variant.id === selectedVariantId) ?? null
  const isBulkWeight = product.inventoryPolicy === 'BULK_WEIGHT'
  const hasStock = isBulkWeight
    ? (product.stockAvailableBaseGrams ?? 0) >= 100 && product.variants.some((variant) => variant.stockAvailable > 0)
    : product.stockAvailable > 0

  function handleAdd() {
    if (!selectedVariantId) {
      setError('Selecciona una variante para continuar.')
      return
    }

    setError(null)
    onAddToCart({
      productId: product.id,
      variantId: selectedVariantId,
      quantity,
    })
  }

  return (
    <article className="product-card">
      <p className="product-category">{product.categoryName}</p>
      <h3 className="product-name">{product.name}</h3>
      <p className="product-description">{product.description}</p>
      <div className="product-footer">
        <strong className="product-price">
          {product.isMultiVariant ? `Desde ${currencyFormatter.format(product.minPrice)}` : currencyFormatter.format(product.price)}
        </strong>
      </div>
      <label className="product-field">
        <span>Presentacion</span>
        <select
          aria-label={`Presentacion para ${product.name}`}
          value={selectedVariantId}
          onChange={(event) => setSelectedVariantId(event.target.value)}
        >
          <option value="">Seleccionar</option>
          {product.variants.map((variant) => (
            <option key={variant.id} value={variant.id} disabled={variant.stockAvailable <= 0}>
              {variant.stockAvailable <= 0 ? `${variant.unitLabel} - sin stock` : variant.unitLabel}
            </option>
          ))}
        </select>
      </label>
      <label className="product-field">
        <span>Cantidad</span>
        <input
          aria-label={`Cantidad para ${product.name}`}
          type="number"
          min={1}
          value={quantity}
          onChange={(event) => setQuantity(Math.max(1, Number(event.target.value) || 1))}
        />
      </label>
      <p className="product-unit">{selectedVariant?.unitLabel ?? product.unitLabel}</p>
      {!hasStock ? <p className="product-validation-error">Sin stock para esta presentación.</p> : null}
      {error ? <p className="product-validation-error">{error}</p> : null}
      <button
        type="button"
        className="btn btn-secondary"
        disabled={!hasStock}
        onClick={handleAdd}
      >
        {!hasStock ? 'Sin stock' : 'Agregar'}
      </button>
    </article>
  )
}
