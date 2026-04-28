import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './AppRoutes'

vi.mock('../../shared/config/flags', () => ({
  adminDashboardEnabled: false,
}))

describe('AppRoutes admin flag', () => {
  it('redirects /admin to storefront when admin flag is disabled', () => {
    window.sessionStorage.setItem('admin-session-role', 'admin')

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="*" element={<AppRoutes homeContent={<h1>Storefront</h1>} checkoutReturnContent={null} />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Storefront' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Panel admin' })).not.toBeInTheDocument()
  })
})
