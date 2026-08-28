import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { logoutAdmin } from '../auth/adminAuthApi'

type AdminTheme = 'dark' | 'light'

const adminThemeStorageKey = 'eltano-admin-theme'

const adminNavItems = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/productos', label: 'Productos' },
  { to: '/admin/categorias', label: 'Categorías' },
  { to: '/admin/pedidos', label: 'Pedidos' },
  { to: '/admin/compras', label: 'Compras' },
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
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutFailed, setLogoutFailed] = useState(false)

  useEffect(() => {
    document.body.dataset.adminTheme = adminTheme

    return () => {
      delete document.body.dataset.adminTheme
    }
  }, [adminTheme])

  async function handleLogout() {
    if (loggingOut) return
    setLoggingOut(true)
    setLogoutFailed(false)
    try {
      await logoutAdmin()
      navigate('/admin/login', { replace: true })
    } catch {
      setLoggingOut(false)
      setLogoutFailed(true)
    }
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
        {logoutFailed ? (
          <p className="admin-feedback admin-feedback-error" role="alert">
            No pudimos cerrar la sesión. Intenta nuevamente.
          </p>
        ) : null}
        <button className="admin-logout" type="button" onClick={handleLogout} disabled={loggingOut}>
          {loggingOut ? 'Cerrando sesión…' : logoutFailed ? 'Reintentar cierre de sesión' : 'Cerrar sesión'}
        </button>
      </aside>
      <section className="admin-content" aria-label="Admin content">
        <Outlet />
      </section>
    </main>
  )
}
