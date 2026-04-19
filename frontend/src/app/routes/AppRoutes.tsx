import type { ReactNode } from 'react'
import { Route, Routes } from 'react-router-dom'
import { CategoriesPage } from '../../features/catalog/pages/CategoriesPage'
import { CategoryDetailPage } from '../../features/catalog/pages/CategoryDetailPage'
import { ProductsPage } from '../../features/catalog/pages/ProductsPage'

interface AppRoutesProps {
  homeContent: ReactNode
  checkoutReturnContent: ReactNode
}

export function AppRoutes({ homeContent, checkoutReturnContent }: AppRoutesProps) {
  return (
    <Routes>
      <Route path="/" element={homeContent} />
      <Route path="/checkout/return" element={checkoutReturnContent} />
      <Route path="/categorias" element={<CategoriesPage />} />
      <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
      <Route path="/productos" element={<ProductsPage />} />
    </Routes>
  )
}
