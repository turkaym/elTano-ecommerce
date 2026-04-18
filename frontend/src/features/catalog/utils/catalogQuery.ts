import type { CatalogListItem, CatalogQueryState, CatalogSort } from '../../../shared/types/catalog'

function normalizeSearchTerm(value: string) {
  return value.trim().toLowerCase()
}

function compareBySort(sort: CatalogSort) {
  return (left: CatalogListItem, right: CatalogListItem) => {
    switch (sort) {
      case 'name-desc':
        return right.name.localeCompare(left.name, 'es')
      case 'price-asc':
        return left.price - right.price
      case 'price-desc':
        return right.price - left.price
      case 'name-asc':
      default:
        return left.name.localeCompare(right.name, 'es')
    }
  }
}

export function normalizeCategorySlug(value: string) {
  return value.trim().toLowerCase().replace(/\s+/g, '-').replace(/-+/g, '-')
}

export function resolveCategoryNameBySlug(items: CatalogListItem[], slug: string): string | null {
  const normalizedSlug = normalizeCategorySlug(slug)
  const matchedItem = items.find((item) => normalizeCategorySlug(item.categorySlug) === normalizedSlug)
  return matchedItem?.categoryName ?? null
}

export function applyCatalogQuery(
  items: CatalogListItem[],
  query: CatalogQueryState,
  routeCategorySlug?: string,
): CatalogListItem[] {
  const searchTerm = normalizeSearchTerm(query.q)
  const routeSlug = routeCategorySlug ? normalizeCategorySlug(routeCategorySlug) : null
  const selectedCategory = query.category ? normalizeCategorySlug(query.category) : null

  if (routeSlug && !resolveCategoryNameBySlug(items, routeSlug)) {
    return []
  }

  return items
    .filter((item) => {
      if (!searchTerm) {
        return true
      }

      const searchableText = `${item.name} ${item.description} ${item.categoryName}`.toLowerCase()
      return searchableText.includes(searchTerm)
    })
    .filter((item) => {
      if (!selectedCategory) {
        return true
      }

      return normalizeCategorySlug(item.categorySlug) === selectedCategory
    })
    .filter((item) => {
      if (!routeSlug) {
        return true
      }

      return normalizeCategorySlug(item.categorySlug) === routeSlug
    })
    .filter((item) => (query.stock === 'in-stock' ? item.stockAvailable > 0 : true))
    .sort(compareBySort(query.sort))
}
