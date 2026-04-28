import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AdminGuard } from './AdminGuard'

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

  it('renders the protected admin shell for an authenticated admin', () => {
    window.sessionStorage.setItem('admin-session-role', 'admin')

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

    expect(screen.getByRole('heading', { name: 'Panel admin' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Storefront' })).not.toBeInTheDocument()
  })

  it('redirects unauthorized users away from /admin', () => {
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

    expect(screen.getByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Panel admin' })).not.toBeInTheDocument()
  })

  it('preserves origin pathname when redirecting unauthorized users', () => {
    render(
      <MemoryRouter initialEntries={['/admin/catalogo']}>
        <Routes>
          <Route element={<AdminGuard />}>
            <Route path="/admin/catalogo" element={<h1>Panel admin</h1>} />
          </Route>
          <Route path="/" element={<StorefrontLanding />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
    expect(screen.getByTestId('redirect-from')).toHaveTextContent('/admin/catalogo')
  })
})
