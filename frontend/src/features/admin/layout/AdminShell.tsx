import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearAdminSessionMarker } from '../auth/adminAccess'

const adminNavItems = [
  { to: '/admin', label: 'Dashboard', end: true },
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
    <main className="admin-shell" aria-label="Panel admin">
      <aside className="admin-sidebar" aria-label="Admin sidebar">
        <div className="admin-brand" aria-label="El Tano admin">
          <span className="admin-brand-mark" aria-hidden="true">ET</span>
          <div>
            <strong>El Tano</strong>
            <span>Admin</span>
          </div>
        </div>
        <nav className="admin-nav" aria-label="Admin workflows">
          <p className="admin-nav-section">Menu</p>
          <ul>
            {adminNavItems.map((item) => (
              <li key={item.to}>
                <NavLink end={item.end} to={item.to} className={({ isActive }) => `admin-nav-link${isActive ? ' admin-nav-link-active' : ''}`}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <button className="admin-logout" type="button" onClick={handleLogout}>
          Cerrar sesión
        </button>
      </aside>
      <section className="admin-content" aria-label="Admin content">
        <Outlet />
      </section>
    </main>
  )
}
