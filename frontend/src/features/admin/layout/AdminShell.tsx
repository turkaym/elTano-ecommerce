import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { clearAdminSessionMarker } from '../auth/adminAccess'

type AdminTheme = 'dark' | 'light'

const adminThemeStorageKey = 'eltano-admin-theme'

const adminNavItems = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/productos', label: 'Productos' },
  { to: '/admin/categorias', label: 'Categorías' },
  { to: '/admin/pedidos', label: 'Pedidos' },
  { to: '/admin/catalog-jobs', label: 'Catalog Jobs' },
]

function isAdminTheme(value: string | null): value is AdminTheme {
  return value === 'dark' || value === 'light'
}

function readStoredAdminTheme(): AdminTheme {
  try {
    const storedTheme = window.localStorage.getItem(adminThemeStorageKey)
    return isAdminTheme(storedTheme) ? storedTheme : 'dark'
  } catch {
    return 'dark'
  }
}

function writeStoredAdminTheme(theme: AdminTheme) {
  try {
    window.localStorage.setItem(adminThemeStorageKey, theme)
  } catch {
    // Theme persistence is a progressive enhancement; keep the in-memory mode usable.
  }
}

export function AdminShell() {
  const navigate = useNavigate()
  const [adminTheme, setAdminTheme] = useState<AdminTheme>(() => readStoredAdminTheme())

  useEffect(() => {
    document.body.dataset.adminTheme = adminTheme

    return () => {
      delete document.body.dataset.adminTheme
    }
  }, [adminTheme])

  function handleLogout() {
    clearAdminSessionMarker()
    navigate('/', { replace: true })
  }

  function handleThemeToggle() {
    setAdminTheme((currentTheme) => {
      const nextTheme = currentTheme === 'dark' ? 'light' : 'dark'
      writeStoredAdminTheme(nextTheme)
      return nextTheme
    })
  }

  return (
    <main className="admin-shell" aria-label="Panel admin" data-admin-theme={adminTheme}>
      <aside className="admin-sidebar" aria-label="Admin sidebar">
        <div className="admin-brand" aria-label="El Tano admin">
          <img className="admin-brand-logo" src="/logo.png" alt="" aria-hidden="true" />
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
        <button className="admin-theme-toggle" type="button" onClick={handleThemeToggle}>
          {adminTheme === 'dark' ? 'Modo claro' : 'Modo oscuro'}
        </button>
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
