import type { ReactNode } from 'react'
import { Route, Routes, useParams } from 'react-router-dom'

interface AppRoutesProps {
  homeContent: ReactNode
  checkoutReturnContent: ReactNode
}

function CategoriesPage() {
  return (
    <main className="main-content">
      <section className="section" aria-labelledby="categorias-title">
        <h2 id="categorias-title">Categorias</h2>
      </section>
    </main>
  )
}

function CategoryDetailPage() {
  const { slug = '' } = useParams()

  return (
    <main className="main-content">
      <section className="section" aria-labelledby="categoria-detalle-title">
        <h2 id="categoria-detalle-title">Categoria: {slug}</h2>
      </section>
    </main>
  )
}

function ProductsPage() {
  return (
    <main className="main-content">
      <section className="section" aria-labelledby="productos-title">
        <h2 id="productos-title">Productos</h2>
      </section>
    </main>
  )
}

export function AppRoutes({ homeContent, checkoutReturnContent }: AppRoutesProps) {
  return (
    <Routes>
      <Route path="/" element={homeContent} />
      <Route path="/categorias" element={<CategoriesPage />} />
      <Route path="/categorias/:slug" element={<CategoryDetailPage />} />
      <Route path="/productos" element={<ProductsPage />} />
      <Route path="/checkout/return" element={checkoutReturnContent} />
    </Routes>
  )
}
