import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { Route, Routes } from 'react-router-dom'
import { AdminGuard } from '../../features/admin/auth/AdminGuard'
import { AdminShell } from '../../features/admin/layout/AdminShell'
import { AdminCatalogJobsPage } from '../../features/admin/pages/AdminCatalogJobsPage'
import { AdminCategoriesPage } from '../../features/admin/pages/AdminCategoriesPage'
import { AdminOrdersPage } from '../../features/admin/pages/AdminOrdersPage'
import { AdminProductsPage } from '../../features/admin/pages/AdminProductsPage'
import { CategoriesPage } from '../../features/catalog/pages/CategoriesPage'
import { CategoryDetailPage } from '../../features/catalog/pages/CategoryDetailPage'
import { ProductsPage } from '../../features/catalog/pages/ProductsPage'
import { adminDashboardEnabled } from '../../shared/config/flags'

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
      {adminDashboardEnabled ? (
        <Route element={<AdminGuard />}>
          <Route path="/admin" element={<AdminShell />}>
            <Route index element={<Navigate to="productos" replace />} />
            <Route path="productos" element={<AdminProductsPage />} />
            <Route path="categorias" element={<AdminCategoriesPage />} />
            <Route path="pedidos" element={<AdminOrdersPage />} />
            <Route path="catalog-jobs" element={<AdminCatalogJobsPage />} />
          </Route>
        </Route>
      ) : (
        <Route path="/admin" element={<Navigate to="/" replace />} />
      )}
    </Routes>
  )
}
