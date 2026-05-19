import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCartStore } from '../../cart/state/cartStore'
import { CatalogProductGrid } from '../components/CatalogProductGrid'
import type { FeaturedAddToCartPayload } from '../components/FeaturedProductsSection'
import { useCatalogQuery } from '../hooks/useCatalogQuery'
import type { CatalogSort, CatalogStockFilter } from '../../../shared/types/catalog'
import type { CartItem } from '../../../shared/types/checkout'

interface ProductsPageProps {
  onAddCartItem?: (item: CartItem) => void
}

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

export function ProductsPage({ onAddCartItem }: ProductsPageProps) {
  const [searchParams, setSearchParams] = useSearchParams()
  const cart = useCartStore()
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
  const totalProductCount = categories.reduce((total, category) => total + category.count, 0)

  function updateQueryParams(updates: {
    q?: string
    category?: string
    stock?: CatalogStockFilter
    sort?: CatalogSort
  }) {
    const nextParams = new URLSearchParams(searchParams)

    if (updates.q !== undefined) {
      if (updates.q.trim()) {
        nextParams.set('q', updates.q)
      } else {
        nextParams.delete('q')
      }
    }

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

  function handleAddToCart(payload: FeaturedAddToCartPayload) {
    const selectedProduct = items.find((product) => product.id === payload.productId)
    const selectedVariant = selectedProduct?.variants.find((variant) => variant.id === payload.variantId)
    if (!selectedProduct || !selectedVariant) {
      return
    }

    const addItem = onAddCartItem ?? cart.addItem
    addItem({
      variantId: selectedVariant.id,
      productName: selectedProduct.name,
      unitLabel: selectedVariant.unitLabel,
      price: selectedVariant.price,
      stockAvailable: selectedVariant.stockAvailable,
      quantity: payload.quantity,
      productId: selectedProduct.id,
      categoryName: selectedProduct.categoryName,
      imageUrl: selectedProduct.primaryImageUrl ?? undefined,
      imageAltText: selectedProduct.primaryImageAltText,
    })
  }

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="productos-title">
        <div className="catalog-layout">
          <aside className="catalog-sidebar">
            <nav className="products-category-sidebar" aria-label="Categorías">
              <h2 className="products-controls-title">Categorías</h2>
              <button
                type="button"
                className={selectedCategory ? 'category-pill' : 'category-pill category-pill-active'}
                aria-current={selectedCategory ? undefined : 'page'}
                onClick={() => updateQueryParams({ category: '' })}
              >
                Todos los productos ({totalProductCount})
              </button>
              {categories.map((category) => (
                <button
                  key={category.slug}
                  type="button"
                  className={selectedCategory === category.slug ? 'category-pill category-pill-active' : 'category-pill'}
                  aria-current={selectedCategory === category.slug ? 'page' : undefined}
                  onClick={() => updateQueryParams({ category: category.slug })}
                >
                  {category.name} ({category.count})
                </button>
              ))}
            </nav>
          </aside>

          <div className="catalog-products-panel">
            <div className="section-header">
              <h2 id="productos-title">Productos</h2>
              <p>Catalogo general ordenado alfabeticamente (A-Z).</p>
            </div>

            <div className="products-controls" aria-label="Filtros de productos">
              <p className="products-controls-title">Filtrar y ordenar</p>

              <label className="products-control-field products-search-field">
                <span className="products-control-label">Filtrar productos</span>
                <input
                  aria-label="Filtrar productos"
                  type="search"
                  value={searchParams.get('q') ?? ''}
                  onChange={(event) => updateQueryParams({ q: event.target.value })}
                  placeholder="Buscar por nombre, descripcion o categoria"
                />
              </label>

              <div className="products-controls-grid">
                <label className="products-control-field">
                  <span className="products-control-label">Stock</span>
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

                <label className="products-control-field">
                  <span className="products-control-label">Ordenar</span>
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
            </div>

            <CatalogProductGrid
              items={items}
              isLoading={isLoading}
              emptyTitle="Sin resultados"
              emptyDescription="No encontramos productos para mostrar en este momento."
              onAddToCart={handleAddToCart}
            />
          </div>
        </div>
      </section>
    </main>
  )
}
