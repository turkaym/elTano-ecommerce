import { useEffect, useRef, useState } from 'react'
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

function normalizeUnitLabel(value: string): string {
  return value.toLowerCase().replace(/\s+/g, '')
}

function findReferenceVariant(product: FeaturedProduct) {
  const inStockVariants = product.variants.filter((variant) => variant.stockAvailable > 0)
  const variants = inStockVariants.length ? inStockVariants : product.variants
  if (!variants.length) {
    return null
  }

  const oneKilogramVariant = variants.find((variant) => variant.weightGrams === 1000)
    ?? variants.find((variant) => /(^|[^0-9])1\s*kg([^a-z]|$)/i.test(variant.unitLabel))
  if (oneKilogramVariant) {
    return oneKilogramVariant
  }

  const oneLiterVariant = variants.find((variant) => {
    const label = normalizeUnitLabel(variant.unitLabel)
    return label.includes('1l') || label.includes('1lt') || label.includes('1litro')
  })
  if (oneLiterVariant) {
    return oneLiterVariant
  }

  return variants.reduce((largest, variant) => {
    const largestWeight = largest.weightGrams ?? 0
    const variantWeight = variant.weightGrams ?? 0
    return variantWeight > largestWeight ? variant : largest
  }, variants[0])
}

function resolveInitialVariantId(product: FeaturedProduct): string {
  if (product.variantId && product.variants.some((variant) => variant.id === product.variantId)) {
    return product.variantId
  }

  return findReferenceVariant(product)?.id ?? product.variants.find((variant) => variant.stockAvailable > 0)?.id ?? ''
}

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
  const initialVariantId = resolveInitialVariantId(product)
  const [selectedVariantId, setSelectedVariantId] = useState<string>(initialVariantId)
  const [quantity, setQuantity] = useState(1)
  const [error, setError] = useState<string | null>(null)
  const [recentlyAdded, setRecentlyAdded] = useState(false)
  const [feedbackId, setFeedbackId] = useState(0)
  const feedbackTimeoutRef = useRef<number | null>(null)
  const selectedVariant = product.variants.find((variant) => variant.id === selectedVariantId) ?? null
  const visiblePrice = selectedVariant?.price ?? product.price
  const maxQuantity = Math.max(1, selectedVariant?.stockAvailable ?? product.stockAvailable)
  const isBulkWeight = product.inventoryPolicy === 'BULK_WEIGHT'
  const hasStock = isBulkWeight
    ? (product.stockAvailableBaseGrams ?? 0) >= 100 && product.variants.some((variant) => variant.stockAvailable > 0)
    : product.stockAvailable > 0

  useEffect(() => {
    return () => {
      if (feedbackTimeoutRef.current !== null) {
        window.clearTimeout(feedbackTimeoutRef.current)
      }
    }
  }, [])

  function handleAdd() {
    if (!selectedVariantId) {
      setError('Selecciona una variante para continuar.')
      return
    }

    setError(null)
    onAddToCart({
      productId: product.id,
      variantId: selectedVariantId,
      quantity: Math.min(quantity, maxQuantity),
    })
    setRecentlyAdded(true)
    setFeedbackId((current) => current + 1)
    if (feedbackTimeoutRef.current !== null) {
      window.clearTimeout(feedbackTimeoutRef.current)
    }
    feedbackTimeoutRef.current = window.setTimeout(() => {
      setRecentlyAdded(false)
      feedbackTimeoutRef.current = null
    }, 1800)
  }

  return (
    <article className="product-card">
      <ProductCardMedia product={product} />
      <p className="product-category">{product.categoryName}</p>
      <h3 className="product-name">{product.name}</h3>
      <p className="product-description">{product.description}</p>
      <div className="product-footer">
        <strong className="product-price">
          {currencyFormatter.format(visiblePrice)}
        </strong>
      </div>
      <div className="product-card-actions">
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
            max={maxQuantity}
            value={quantity}
            onChange={(event) => setQuantity(Math.min(maxQuantity, Math.max(1, Number(event.target.value) || 1)))}
          />
        </label>
        <p className="product-unit">{selectedVariant?.unitLabel ?? product.unitLabel}</p>
        <div className="product-card-messages" aria-live="polite">
          {!hasStock ? <p className="product-validation-error">Sin stock para esta presentación.</p> : null}
          {error ? <p className="product-validation-error">{error}</p> : null}
          {recentlyAdded ? <span key={feedbackId} className="product-added-feedback" role="status">Producto agregado</span> : null}
        </div>
        <button
          type="button"
          className="btn btn-primary product-add-button"
          disabled={!hasStock}
          onClick={handleAdd}
        >
          {!hasStock ? 'Sin stock' : recentlyAdded ? 'Agregado' : 'Agregar'}
        </button>
      </div>
    </article>
  )
}

export function ProductCardMedia({ product }: { product: FeaturedProduct }) {
  if (product.primaryImageUrl) {
    return (
      <div className="product-media">
        <img
          src={product.primaryImageUrl}
          alt={product.primaryImageAltText ?? product.name}
          loading="lazy"
        />
      </div>
    )
  }

  return (
    <div className="product-media product-media-placeholder" aria-label={`Imagen no disponible para ${product.name}`}>
      <span>Sin imagen</span>
    </div>
  )
}
