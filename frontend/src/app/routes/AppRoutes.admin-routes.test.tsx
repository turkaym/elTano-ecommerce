import { render, screen, waitForElementToBeRemoved } from '@testing-library/react'
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
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function renderAdminRoutes() {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="*" element={<AppRoutes homeContent={<h1>Storefront</h1>} checkoutReturnContent={null} />} />
        </Routes>
      </MemoryRouter>,
    )
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

    renderAdminRoutes()

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

    renderAdminRoutes()

    expect(await screen.findByRole('heading', { name: 'Dashboard admin' })).toBeInTheDocument()
    expect(bootstrapSpy).toHaveBeenCalledTimes(1)
    expect(listSpy).toHaveBeenCalledTimes(1)
  })
})
