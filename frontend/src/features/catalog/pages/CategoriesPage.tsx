import { Link } from 'react-router-dom'
import { useCatalogQuery } from '../hooks/useCatalogQuery'

const defaultQuery = {
  q: '',
  category: null,
  stock: 'all' as const,
  sort: 'name-asc' as const,
}

export function CategoriesPage() {
  const { categories, isLoading } = useCatalogQuery(defaultQuery)

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="categorias-title">
        <div className="section-header">
          <h2 id="categorias-title">Categorias</h2>
          <p>Explora el catalogo por rubro y entra directo a cada seleccion.</p>
        </div>

        {isLoading ? (
          <p className="products-loading" role="status">
            Cargando categorias...
          </p>
        ) : categories.length ? (
          <ul className="category-list" aria-label="Listado de categorias">
            {categories.map((category) => (
              <li key={category.slug}>
                <Link className="category-list-link" to={`/categorias/${category.slug}`}>
                  <span>{category.name}</span>
                  <small>{category.count} productos</small>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="products-hint" role="status">
            No hay categorias disponibles en este momento.
          </p>
        )}
      </section>
    </main>
  )
}
