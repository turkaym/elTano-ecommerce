import type { CatalogListItem } from '../../../shared/types/catalog'
import { FeaturedProductCard, ProductCardMedia, type FeaturedAddToCartPayload } from './FeaturedProductsSection'

interface CatalogEmptyStateProps {
  title: string
  description: string
}

interface CatalogProductGridProps {
  items: CatalogListItem[]
  isLoading?: boolean
  emptyTitle: string
  emptyDescription: string
  onAddToCart?: (payload: FeaturedAddToCartPayload) => void
}

export function CatalogEmptyState({ title, description }: CatalogEmptyStateProps) {
  return (
    <div className="catalog-empty" role="status">
      <h3 className="catalog-empty-title">{title}</h3>
      <p>{description}</p>
    </div>
  )
}

export function CatalogProductGrid({
  items,
  isLoading = false,
  emptyTitle,
  emptyDescription,
  onAddToCart,
}: CatalogProductGridProps) {
  if (isLoading) {
    return (
      <div className="catalog-empty catalog-empty-loading" role="status">
        <p className="catalog-empty-title">Cargando catálogo...</p>
        <p>Estamos preparando los productos para vos.</p>
      </div>
    )
  }

  if (!items.length) {
    return <CatalogEmptyState title={emptyTitle} description={emptyDescription} />
  }

  return (
    <div className="products-grid">
      {items.map((product) => (
        onAddToCart ? (
          <FeaturedProductCard key={product.id} product={product} onAddToCart={onAddToCart} />
        ) : (
          <CatalogReadOnlyProductCard key={product.id} product={product} />
        )
      ))}
    </div>
  )
}

const currencyFormatter = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

function CatalogReadOnlyProductCard({ product }: { product: CatalogListItem }) {
  return (
    <article className="product-card">
      <ProductCardMedia product={product} />
      <p className="product-category">{product.categoryName}</p>
      <h3 className="product-name">{product.name}</h3>
      <p className="product-description">{product.description}</p>
      <p className="product-unit">{product.unitLabel}</p>
      <div className="product-footer">
        <strong className="product-price">
          {product.isMultiVariant
            ? `Desde ${currencyFormatter.format(product.minPrice)}`
            : currencyFormatter.format(product.price)}
        </strong>
      </div>
    </article>
  )
}
