import { useEffect, useMemo, useState } from 'react'
import type {
  CatalogCategorySummary,
  CatalogListItem,
  CatalogQueryResult,
  CatalogQueryState,
} from '../../../shared/types/catalog'
import { getCatalogListItems } from '../services/catalogQueryService'
import { applyCatalogQuery, normalizeCategorySlug } from '../utils/catalogQuery'

export interface UseCatalogQueryOptions {
  routeCategorySlug?: string
}

function getCategories(items: CatalogListItem[]): CatalogCategorySummary[] {
  const categoriesMap = new Map<string, CatalogCategorySummary>()

  for (const item of items) {
    const key = normalizeCategorySlug(item.categorySlug)
    const existing = categoriesMap.get(key)

    if (!existing) {
      categoriesMap.set(key, {
        name: item.categoryName,
        slug: key,
        count: 1,
      })
      continue
    }

    existing.count += 1
  }

  return Array.from(categoriesMap.values()).sort((left, right) => left.name.localeCompare(right.name, 'es'))
}

export function useCatalogQuery(
  query: CatalogQueryState,
  options: UseCatalogQueryOptions = {},
): CatalogQueryResult {
  const [allItems, setAllItems] = useState<CatalogListItem[]>([])
  const [source, setSource] = useState<CatalogQueryResult['source']>('mock')
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let isMounted = true

    async function loadItems() {
      const result = await getCatalogListItems()

      if (!isMounted) {
        return
      }

      setAllItems(result.items)
      setSource(result.source)
      setIsLoading(false)
    }

    void loadItems()

    return () => {
      isMounted = false
    }
  }, [])

  const items = useMemo(
    () => applyCatalogQuery(allItems, query, options.routeCategorySlug),
    [allItems, options.routeCategorySlug, query],
  )

  const categories = useMemo(() => getCategories(allItems), [allItems])

  return {
    source,
    isLoading,
    items,
    categories,
  }
}
