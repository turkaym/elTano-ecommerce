import { render, screen, waitForElementToBeRemoved, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as adminAccess from '../../features/admin/auth/adminAccess'
import * as adminOperationsService from '../../features/admin/services/adminOperationsService'
import { AppRoutes } from './AppRoutes'

vi.mock('../../shared/config/flags', () => ({
  adminDashboardEnabled: true,
}))

describe('AppRoutes admin nested routes', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  function mockAuthenticatedAdminServices() {
    vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('authenticated')
    vi.spyOn(adminOperationsService, 'listAdminProducts').mockResolvedValue([])
    vi.spyOn(adminOperationsService, 'listAdminCategories').mockResolvedValue([])
    vi.spyOn(adminOperationsService, 'listAdminOrders').mockResolvedValue({
      items: [],
      page: 0,
      size: 8,
      totalElements: 0,
      totalPages: 0,
    })
    vi.spyOn(adminOperationsService, 'listAdminCatalogJobs').mockResolvedValue([])
  }

  function renderRoutes(initialEntry = '/admin') {
    render(
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="*" element={<AppRoutes homeContent={<h1>Storefront</h1>} checkoutReturnContent={null} />} />
        </Routes>
      </MemoryRouter>,
    )
  }

  async function renderReadyAdminRoute(initialEntry: string, readyText: string | RegExp) {
    mockAuthenticatedAdminServices()
    renderRoutes(initialEntry)
    expect(await screen.findByText(readyText)).toBeInTheDocument()
  }

  it('shows admin loading boundaries then settles on the dashboard landing page', async () => {
    let resolveBootstrap: ((value: 'authenticated') => void) | undefined
    const bootstrapPromise = new Promise<'authenticated'>((resolve) => {
      resolveBootstrap = resolve
    })

    let resolveProducts: ((value: []) => void) | undefined
    const productsPromise = new Promise<[]>((resolve) => {
      resolveProducts = resolve
    })

    const bootstrapSpy = vi.spyOn(adminAccess, 'bootstrapAdminSession').mockReturnValue(bootstrapPromise)
    const listSpy = vi.spyOn(adminOperationsService, 'listAdminProducts').mockReturnValue(productsPromise)
    const ordersSpy = vi.spyOn(adminOperationsService, 'listAdminOrders').mockResolvedValue({
      items: [],
      page: 0,
      size: 8,
      totalElements: 0,
      totalPages: 0,
    })

    renderRoutes()

    expect(await screen.findByRole('heading', { name: 'Verificando acceso admin…' })).toBeInTheDocument()
    resolveBootstrap?.('authenticated')
    await waitForElementToBeRemoved(() => screen.queryByRole('heading', { name: 'Verificando acceso admin…' }))
    expect(await screen.findByText('Cargando dashboard admin…')).toBeInTheDocument()
    resolveProducts?.([])
    await waitForElementToBeRemoved(() => screen.queryByText('Cargando dashboard admin…'))

    expect(await screen.findByRole('heading', { name: 'Dashboard admin' })).toBeInTheDocument()
    expect(bootstrapSpy).toHaveBeenCalledTimes(1)
    expect(listSpy).toHaveBeenCalledTimes(1)
    expect(ordersSpy).toHaveBeenCalledWith({ page: 0, size: 8 })
  })

  it('isolates seam mocks to avoid reused state between tests', async () => {
    const bootstrapSpy = vi.spyOn(adminAccess, 'bootstrapAdminSession').mockResolvedValue('authenticated')
    const listSpy = vi.spyOn(adminOperationsService, 'listAdminProducts').mockResolvedValue([])
    vi.spyOn(adminOperationsService, 'listAdminOrders').mockResolvedValue({
      items: [],
      page: 0,
      size: 8,
      totalElements: 0,
      totalPages: 0,
    })

    renderRoutes()

    expect(await screen.findByRole('heading', { name: 'Dashboard admin' })).toBeInTheDocument()
    expect(bootstrapSpy).toHaveBeenCalledTimes(1)
    expect(listSpy).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['/admin', 'Dashboard admin'],
    ['/admin/productos', 'Productos'],
    ['/admin/categorias', 'Categorías'],
    ['/admin/pedidos', 'Pedidos'],
    ['/admin/catalog-jobs', 'Sin jobs todavía'],
  ])('shows the persistent sidebar links on %s', async (initialEntry, readyText) => {
    await renderReadyAdminRoute(initialEntry, readyText)

    const sidebar = screen.getByRole('complementary', { name: 'Admin sidebar' })
    expect(within(sidebar).getByText('El Tano')).toBeInTheDocument()
    expect(within(sidebar).queryByText(/Backoffice/i)).not.toBeInTheDocument()
    expect(within(sidebar).queryByText(/Gestiona catálogo/i)).not.toBeInTheDocument()
    const navigation = within(sidebar).getByRole('navigation', { name: 'Admin workflows' })

    const expectedLinks = [
      ['Dashboard', '/admin'],
      ['Productos', '/admin/productos'],
      ['Categorías', '/admin/categorias'],
      ['Pedidos', '/admin/pedidos'],
      ['Catalog Jobs', '/admin/catalog-jobs'],
    ]

    for (const [label, href] of expectedLinks) {
      expect(within(navigation).getByRole('link', { name: label })).toHaveAttribute('href', href)
    }
  })

  it.each([
    ['/admin', 'Dashboard admin', 'Dashboard'],
    ['/admin/pedidos', 'Pedidos', 'Pedidos'],
  ])('marks the current admin destination active on %s', async (initialEntry, readyText, activeLabel) => {
    await renderReadyAdminRoute(initialEntry, readyText)

    const sidebar = screen.getByRole('complementary', { name: 'Admin sidebar' })
    expect(within(sidebar).getByRole('link', { name: activeLabel })).toHaveAttribute('aria-current', 'page')
  })

  it('keeps admin logout accessible from the sidebar and returns to storefront', async () => {
    const user = userEvent.setup()
    mockAuthenticatedAdminServices()
    const clearSessionSpy = vi.spyOn(adminAccess, 'clearAdminSessionMarker').mockImplementation(() => undefined)
    renderRoutes('/admin')

    expect(await screen.findByRole('heading', { name: 'Dashboard admin' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(clearSessionSpy).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
  })

  it('defaults the admin shell to dark mode and lets admins switch to light mode', async () => {
    const user = userEvent.setup()
    await renderReadyAdminRoute('/admin', 'Dashboard admin')

    const shell = screen.getByRole('main', { name: 'Panel admin' })
    expect(shell).toHaveAttribute('data-admin-theme', 'dark')

    await user.click(screen.getByRole('button', { name: /modo claro/i }))

    expect(shell).toHaveAttribute('data-admin-theme', 'light')
    expect(screen.getByRole('button', { name: /modo oscuro/i })).toBeInTheDocument()
    expect(window.localStorage.getItem('eltano-admin-theme')).toBe('light')
  })

  it('restores a persisted light admin theme choice', async () => {
    window.localStorage.setItem('eltano-admin-theme', 'light')

    await renderReadyAdminRoute('/admin/pedidos', 'Pedidos')

    expect(screen.getByRole('main', { name: 'Panel admin' })).toHaveAttribute('data-admin-theme', 'light')
    expect(screen.getByRole('button', { name: /modo oscuro/i })).toBeInTheDocument()
  })

  it('falls back to dark mode when the persisted admin theme value is invalid', async () => {
    window.localStorage.setItem('eltano-admin-theme', 'sepia')

    await renderReadyAdminRoute('/admin', 'Dashboard admin')

    expect(screen.getByRole('main', { name: 'Panel admin' })).toHaveAttribute('data-admin-theme', 'dark')
    expect(screen.getByRole('button', { name: /modo claro/i })).toBeInTheDocument()
  })

  it('does not render the admin sidebar on storefront routes', () => {
    renderRoutes('/')

    expect(screen.getByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary', { name: 'Admin sidebar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Admin workflows' })).not.toBeInTheDocument()
    expect(document.querySelector('[data-admin-theme]')).not.toBeInTheDocument()
  })
})
