import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdminLoginPage } from './AdminLoginPage'
import * as adminAuthApi from './adminAuthApi'

describe('AdminLoginPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('submits credentials once and returns to the intended admin route', async () => {
    const user = userEvent.setup()
    let resolveLogin: (() => void) | undefined
    const loginPromise = new Promise<void>((resolve) => { resolveLogin = resolve })
    const loginSpy = vi.spyOn(adminAuthApi, 'loginAdmin').mockReturnValue(loginPromise)

    render(
      <MemoryRouter initialEntries={[{ pathname: '/admin/login', state: { from: '/admin/compras' } }]}>
        <Routes>
          <Route path="/admin/login" element={<AdminLoginPage />} />
          <Route path="/admin/compras" element={<h1>Compras protegidas</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Usuario'), 'admin-user')
    await user.type(screen.getByLabelText('Contraseña'), 'admin-pass')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
    await user.click(screen.getByRole('button', { name: 'Iniciando sesión…' }))

    expect(loginSpy).toHaveBeenCalledTimes(1)
    expect(loginSpy).toHaveBeenCalledWith('admin-user', 'admin-pass')
    resolveLogin?.()
    expect(await screen.findByRole('heading', { name: 'Compras protegidas' })).toBeInTheDocument()
  })

  it('shows a generic failure without retaining the password', async () => {
    const user = userEvent.setup()
    vi.spyOn(adminAuthApi, 'loginAdmin').mockRejectedValue(new Error('specific backend detail'))
    render(<MemoryRouter><AdminLoginPage /></MemoryRouter>)

    await user.type(screen.getByLabelText('Usuario'), 'admin-user')
    const password = screen.getByLabelText('Contraseña') as HTMLInputElement
    await user.type(password, 'wrong-secret')
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos iniciar sesión')
    expect(password.value).toBe('')
    expect(screen.queryByText('specific backend detail')).not.toBeInTheDocument()
  })
})
