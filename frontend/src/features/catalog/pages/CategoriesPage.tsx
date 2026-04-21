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
          <div className="catalog-empty catalog-empty-loading" role="status">
            <p className="catalog-empty-title">Cargando categorías...</p>
            <p>Estamos preparando el listado por rubros.</p>
          </div>
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
          <div className="catalog-empty" role="status">
            <h3 className="catalog-empty-title">Sin categorías</h3>
            <p>No hay categorias disponibles en este momento.</p>
          </div>
        )}
      </section>
    </main>
  )
}
