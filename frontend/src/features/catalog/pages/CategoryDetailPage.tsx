import { useParams } from 'react-router-dom'
import { useCartStore } from '../../cart/state/cartStore'
import { CatalogProductGrid } from '../components/CatalogProductGrid'
import type { FeaturedAddToCartPayload } from '../components/FeaturedProductsSection'
import { useCatalogQuery } from '../hooks/useCatalogQuery'
import { normalizeCategorySlug } from '../utils/catalogQuery'
import type { CartItem } from '../../../shared/types/checkout'

interface CategoryDetailPageProps {
  onAddCartItem?: (item: CartItem) => void
}

const defaultQuery = {
  q: '',
  category: null,
  stock: 'all' as const,
  sort: 'name-asc' as const,
}

export function CategoryDetailPage({ onAddCartItem }: CategoryDetailPageProps) {
  const { slug = '' } = useParams()
  const cart = useCartStore()
  const { categories, items, isLoading } = useCatalogQuery(defaultQuery, {
    routeCategorySlug: slug,
  })

  const normalizedSlug = normalizeCategorySlug(slug)
  const selectedCategory = categories.find((category) => category.slug === normalizedSlug)
  const isInvalidSlug = !isLoading && !selectedCategory

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
          onAddToCart={isInvalidSlug ? undefined : handleAddToCart}
        />
      </section>
    </main>
  )
}
