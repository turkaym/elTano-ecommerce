import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearAdminSessionMarker } from '../auth/adminAccess'

const adminNavItems = [
  { to: '/admin/productos', label: 'Productos' },
  { to: '/admin/categorias', label: 'Categorías' },
  { to: '/admin/pedidos', label: 'Pedidos' },
  { to: '/admin/catalog-jobs', label: 'Catalog Jobs' },
]

export function AdminShell() {
  const navigate = useNavigate()

  function handleLogout() {
    clearAdminSessionMarker()
    navigate('/', { replace: true })
  }

  return (
    <main className="main-content admin-main" aria-labelledby="admin-title">
      <section className="section admin-hero-card">
        <div className="admin-hero-copy">
          <p className="admin-eyebrow">Backoffice</p>
          <h1 id="admin-title">Panel admin</h1>
          <p>Gestiona catálogo y operaciones internas desde este espacio protegido.</p>
        </div>
        <nav className="admin-nav" aria-label="Admin workflows">
          <ul>
            {adminNavItems.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} className={({ isActive }) => `admin-nav-link${isActive ? ' admin-nav-link-active' : ''}`}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <button className="btn btn-secondary admin-logout" type="button" onClick={handleLogout}>
          Cerrar sesión admin
        </button>
      </section>
      <Outlet />
    </main>
  )
}
