import { CatalogProductGrid } from '../components/CatalogProductGrid'
import { useCatalogQuery } from '../hooks/useCatalogQuery'

const defaultQuery = {
  q: '',
  category: null,
  stock: 'all' as const,
  sort: 'name-asc' as const,
}

export function ProductsPage() {
  const { items, isLoading } = useCatalogQuery(defaultQuery)

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="productos-title">
        <div className="section-header">
          <h2 id="productos-title">Productos</h2>
          <p>Catalogo general ordenado alfabeticamente (A-Z).</p>
        </div>

        <CatalogProductGrid
          items={items}
          isLoading={isLoading}
          emptyTitle="Sin resultados"
          emptyDescription="No encontramos productos para mostrar en este momento."
        />
      </section>
    </main>
  )
}
