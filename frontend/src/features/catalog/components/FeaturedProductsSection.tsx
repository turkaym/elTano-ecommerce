import type { FeaturedProduct } from '../../../shared/types/catalog'

interface FeaturedProductsSectionProps {
  products: FeaturedProduct[]
  isLoading: boolean
  source: 'api' | 'mock'
  onAddToCart: (product: FeaturedProduct) => void
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
        <p className="products-loading" role="status">
          Cargando catalogo...
        </p>
      ) : (
        <div className="products-grid">
          {products.map((product) => (
            <article className="product-card" key={product.id}>
              <p className="product-category">{product.categoryName}</p>
              <h3>{product.name}</h3>
              <p className="product-description">{product.description}</p>
              <p className="product-unit">{product.unitLabel}</p>
              <div className="product-footer">
                <strong>{currencyFormatter.format(product.price)}</strong>
                <span>Stock {product.stockAvailable}</span>
              </div>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={product.stockAvailable <= 0}
                onClick={() => onAddToCart(product)}
              >
                {product.stockAvailable <= 0 ? 'Sin stock' : 'Agregar'}
              </button>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
