import { Outlet } from 'react-router-dom'

export function AdminShell() {
  return (
    <main className="main-content" aria-labelledby="admin-title">
      <section className="section">
        <h1 id="admin-title">Panel admin</h1>
        <p>Gestiona catalogo y operaciones internas desde este espacio protegido.</p>
      </section>
      <Outlet />
    </main>
  )
}
