import type { CatalogListItem } from '../../../shared/types/catalog'

interface CatalogEmptyStateProps {
  title: string
  description: string
}

interface CatalogProductGridProps {
  items: CatalogListItem[]
  isLoading?: boolean
  emptyTitle: string
  emptyDescription: string
}

const currencyFormatter = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

export function CatalogEmptyState({ title, description }: CatalogEmptyStateProps) {
  return (
    <div className="catalog-empty" role="status">
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  )
}

export function CatalogProductGrid({
  items,
  isLoading = false,
  emptyTitle,
  emptyDescription,
}: CatalogProductGridProps) {
  if (isLoading) {
    return (
      <p className="products-loading" role="status">
        Cargando catalogo...
      </p>
    )
  }

  if (!items.length) {
    return <CatalogEmptyState title={emptyTitle} description={emptyDescription} />
  }

  return (
    <div className="products-grid">
      {items.map((product) => (
        <article className="product-card" key={product.id}>
          <p className="product-category">{product.categoryName}</p>
          <h3>{product.name}</h3>
          <p className="product-description">{product.description}</p>
          <p className="product-unit">{product.unitLabel}</p>
          <div className="product-footer">
            <strong>{currencyFormatter.format(product.price)}</strong>
            <span>Stock {product.stockAvailable}</span>
          </div>
        </article>
      ))}
    </div>
  )
}
