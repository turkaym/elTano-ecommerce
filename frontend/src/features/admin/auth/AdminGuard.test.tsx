import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminGuard } from './AdminGuard'
import * as adminAccess from './adminAccess'

describe('AdminGuard', () => {
  function StorefrontLanding() {
    const location = useLocation()
    const from = (location.state as { from?: string } | null)?.from ?? 'none'
    return (
      <>
        <h1>Storefront</h1>
        <p data-testid="redirect-from">{from}</p>
      </>
    )
  }

  beforeEach(() => {
    window.sessionStorage.clear()
  })

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('allows direct /admin load after backend bootstrap 2xx', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('authenticated')

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<h1>Panel admin</h1>} />
          </Route>
          <Route path="/admin/login" element={<h1>Admin login</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Panel admin' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Storefront' })).not.toBeInTheDocument()
  })

  it('redirects unauthenticated users to login-entry UX on 401', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('unauthenticated')

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<h1>Panel admin</h1>} />
          </Route>
          <Route path="/admin/login" element={<h1>Admin login</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Admin login' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Panel admin' })).not.toBeInTheDocument()
  })

  it('renders explicit forbidden UX on 403', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('forbidden')

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<h1>Panel admin</h1>} />
          </Route>
          <Route path="/" element={<h1>Storefront</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Acceso denegado' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Panel admin' })).not.toBeInTheDocument()
  })

  it('keeps CSRF failures distinct from an expired session', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('csrf-failure')

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<h1>Panel admin</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Sesión no validada' })).toBeInTheDocument()
  })

  it('renders backend unavailability without redirecting to login', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockRejectedValue(new TypeError('Failed to fetch'))

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin" element={<h1>Panel admin</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Panel admin temporalmente no disponible' })).toBeInTheDocument()
  })

  it('preserves origin pathname when redirecting unauthenticated users', async () => {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('unauthenticated')

    render(
      <MemoryRouter initialEntries={['/admin/catalogo']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin/catalogo" element={<h1>Panel admin</h1>} />
          </Route>
          <Route path="/admin/login" element={<StorefrontLanding />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
    expect(screen.getByTestId('redirect-from')).toHaveTextContent('/admin/catalogo')
  })
})
