import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CatalogProductGrid } from '../components/CatalogProductGrid'
import { useCatalogQuery } from '../hooks/useCatalogQuery'
import type { CatalogSort, CatalogStockFilter } from '../../../shared/types/catalog'

const defaultQuery = {
  q: '',
  category: null,
  stock: 'all' as const,
  sort: 'name-asc' as const,
}

const sortOptions: Array<{ value: CatalogSort; label: string }> = [
  { value: 'name-asc', label: 'Nombre (A-Z)' },
  { value: 'name-desc', label: 'Nombre (Z-A)' },
  { value: 'price-asc', label: 'Precio (menor a mayor)' },
  { value: 'price-desc', label: 'Precio (mayor a menor)' },
]

function normalizeStock(value: string | null): CatalogStockFilter {
  return value === 'in-stock' ? 'in-stock' : 'all'
}

function normalizeSort(value: string | null): CatalogSort {
  switch (value) {
    case 'name-desc':
    case 'price-asc':
    case 'price-desc':
      return value
    case 'name-asc':
    default:
      return 'name-asc'
  }
}

export function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { items, categories, isLoading } = useCatalogQuery(
    useMemo(
      () => ({
        q: searchParams.get('q') ?? defaultQuery.q,
        category: searchParams.get('category') ?? defaultQuery.category,
        stock: normalizeStock(searchParams.get('stock')),
        sort: normalizeSort(searchParams.get('sort')),
      }),
      [searchParams],
    ),
  )

  const selectedCategory = searchParams.get('category') ?? ''
  const selectedStock = normalizeStock(searchParams.get('stock'))
  const selectedSort = normalizeSort(searchParams.get('sort'))

  function updateQueryParams(updates: {
    category?: string
    stock?: CatalogStockFilter
    sort?: CatalogSort
  }) {
    const nextParams = new URLSearchParams(searchParams)

    if (updates.category !== undefined) {
      if (updates.category) {
        nextParams.set('category', updates.category)
      } else {
        nextParams.delete('category')
      }
    }

    if (updates.stock !== undefined) {
      if (updates.stock === 'all') {
        nextParams.delete('stock')
      } else {
        nextParams.set('stock', updates.stock)
      }
    }

    if (updates.sort !== undefined) {
      if (updates.sort === 'name-asc') {
        nextParams.delete('sort')
      } else {
        nextParams.set('sort', updates.sort)
      }
    }

    setSearchParams(nextParams)
  }

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="productos-title">
        <div className="section-header">
          <h2 id="productos-title">Productos</h2>
          <p>Catalogo general ordenado alfabeticamente (A-Z).</p>
        </div>

        <div className="products-controls" aria-label="Filtros de productos">
          <label>
            Categoria
            <select
              value={selectedCategory}
              onChange={(event) => updateQueryParams({ category: event.target.value })}
            >
              <option value="">Todas</option>
              {categories.map((category) => (
                <option key={category.slug} value={category.slug}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Stock
            <select
              value={selectedStock}
              onChange={(event) =>
                updateQueryParams({ stock: normalizeStock(event.target.value) })
              }
            >
              <option value="all">Todos</option>
              <option value="in-stock">Solo con stock</option>
            </select>
          </label>

          <label>
            Ordenar
            <select
              value={selectedSort}
              onChange={(event) =>
                updateQueryParams({ sort: normalizeSort(event.target.value) })
              }
            >
              {sortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
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
