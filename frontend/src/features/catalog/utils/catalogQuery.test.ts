import { describe, expect, it } from 'vitest'
import type { CatalogListItem, CatalogQueryState } from '../../../shared/types/catalog'
import {
  applyCatalogQuery,
  normalizeCategorySlug,
  resolveCategoryNameBySlug,
} from './catalogQuery'

const baseQuery: CatalogQueryState = {
  q: '',
  category: null,
  stock: 'all',
  sort: 'name-asc',
}

const items: CatalogListItem[] = [
  {
    id: '1',
    variantId: 'v1',
    name: 'Almendra tostada',
    description: 'Snack natural y crocante',
    categoryName: 'Frutos secos',
    categorySlug: 'frutos-secos',
    unitLabel: 'bolsa 500 g',
    price: 5200,
    stockAvailable: 10,
  },
  {
    id: '2',
    variantId: 'v2',
    name: 'Harina de coco',
    description: 'Ideal para panificados',
    categoryName: 'Harinas',
    categorySlug: 'harinas',
    unitLabel: 'bolsa 1 kg',
    price: 4300,
    stockAvailable: 0,
  },
  {
    id: '3',
    variantId: 'v3',
    name: 'Mix premium',
    description: 'Blend con almendras y nueces',
    categoryName: 'Frutos secos',
    categorySlug: 'frutos-secos',
    unitLabel: 'bolsa 350 g',
    price: 6100,
    stockAvailable: 5,
  },
]

describe('catalogQuery utilities', () => {
  it('normalizes category slug from free text', () => {
    expect(normalizeCategorySlug('  Frutos   secos  ')).toBe('frutos-secos')
    expect(normalizeCategorySlug('Semillas y cereales')).toBe('semillas-y-cereales')
  })

  it('resolves category name by slug', () => {
    expect(resolveCategoryNameBySlug(items, 'frutos-secos')).toBe('Frutos secos')
    expect(resolveCategoryNameBySlug(items, 'desconocida')).toBeNull()
  })

  it('matches search by name, description and category with trimmed/lower query', () => {
    expect(applyCatalogQuery(items, { ...baseQuery, q: '  almendra ' }).map((item) => item.id)).toEqual([
      '1',
      '3',
    ])
    expect(applyCatalogQuery(items, { ...baseQuery, q: 'panificados' }).map((item) => item.id)).toEqual([
      '2',
    ])
    expect(applyCatalogQuery(items, { ...baseQuery, q: 'frutos secos' }).map((item) => item.id)).toEqual([
      '1',
      '3',
    ])
  })

  it('applies category and stock filters together', () => {
    const result = applyCatalogQuery(items, {
      ...baseQuery,
      category: 'frutos-secos',
      stock: 'in-stock',
    })

    expect(result.map((item) => item.id)).toEqual(['1', '3'])
  })

  it('supports all sort modes', () => {
    expect(applyCatalogQuery(items, { ...baseQuery, sort: 'name-asc' }).map((item) => item.id)).toEqual([
      '1',
      '2',
      '3',
    ])
    expect(applyCatalogQuery(items, { ...baseQuery, sort: 'name-desc' }).map((item) => item.id)).toEqual([
      '3',
      '2',
      '1',
    ])
    expect(applyCatalogQuery(items, { ...baseQuery, sort: 'price-asc' }).map((item) => item.id)).toEqual([
      '2',
      '1',
      '3',
    ])
    expect(applyCatalogQuery(items, { ...baseQuery, sort: 'price-desc' }).map((item) => item.id)).toEqual([
      '3',
      '1',
      '2',
    ])
  })

  it('returns empty when route slug is unknown', () => {
    const result = applyCatalogQuery(items, baseQuery, 'categoria-invalida')
    expect(result).toEqual([])
  })
})
