import { useParams } from 'react-router-dom'
import { CatalogProductGrid } from '../components/CatalogProductGrid'
import { useCatalogQuery } from '../hooks/useCatalogQuery'
import { normalizeCategorySlug } from '../utils/catalogQuery'

const defaultQuery = {
  q: '',
  category: null,
  stock: 'all' as const,
  sort: 'name-asc' as const,
}

export function CategoryDetailPage() {
  const { slug = '' } = useParams()
  const { categories, items, isLoading } = useCatalogQuery(defaultQuery, {
    routeCategorySlug: slug,
  })

  const normalizedSlug = normalizeCategorySlug(slug)
  const selectedCategory = categories.find((category) => category.slug === normalizedSlug)
  const isInvalidSlug = !isLoading && !selectedCategory

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="categoria-detalle-title">
        <div className="section-header">
          <h2 id="categoria-detalle-title">
            {selectedCategory ? `Categoria: ${selectedCategory.name}` : 'Categoria no encontrada'}
          </h2>
          <p>
            {selectedCategory
              ? `Productos disponibles en ${selectedCategory.name}.`
              : 'No encontramos una categoria para el slug solicitado.'}
          </p>
        </div>

        <CatalogProductGrid
          items={items}
          isLoading={isLoading}
          emptyTitle={isInvalidSlug ? 'Slug de categoria invalido' : 'Sin productos en esta categoria'}
          emptyDescription={
            isInvalidSlug
              ? `No encontramos una categoria para el slug "${normalizedSlug}".`
              : 'Todavia no hay productos publicados para esta categoria.'
          }
        />
      </section>
    </main>
  )
}
