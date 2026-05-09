import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminProductsPage } from './AdminProductsPage'
import {
  createAdminProduct,
  listAdminProducts,
  mapAdminWriteError,
  updateAdminProduct,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminProducts: vi.fn(async () => [{ id: 'p-1', name: 'Nuez' }]),
  createAdminProduct: vi.fn(async () => ({ id: 'p-2', name: 'Pera' })),
  updateAdminProduct: vi.fn(async () => ({ id: 'p-1', name: 'Nuez Premium' })),
  mapAdminWriteError: vi.fn(() => ({ message: 'Error API' })),
}))

describe('AdminProductsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows validation error and blocks submit when name is empty', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Nombre es requerido.')
    expect(vi.mocked(createAdminProduct)).not.toHaveBeenCalled()
  })

  it('creates product and refreshes list', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Pera' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    await waitFor(() => expect(vi.mocked(createAdminProduct)).toHaveBeenCalledWith({ name: 'Pera' }))
    expect(await screen.findByText('Producto guardado correctamente.')).toBeInTheDocument()
    expect(vi.mocked(listAdminProducts).mock.calls.length).toBeGreaterThan(1)
  })

  it('keeps pending state and blocks duplicate submit until create resolves', async () => {
    let resolveCreate: ((value: { id: string; name: string }) => void) | undefined
    vi.mocked(createAdminProduct).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveCreate = resolve
        }),
    )

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Pera' } })
    const submitButton = screen.getByRole('button', { name: /Crear producto/i })
    fireEvent.click(submitButton)
    fireEvent.click(submitButton)

    expect(await screen.findByRole('status')).toHaveTextContent('Guardando cambios…')
    expect(submitButton).toBeDisabled()
    expect(vi.mocked(createAdminProduct)).toHaveBeenCalledTimes(1)

    resolveCreate?.({ id: 'p-2', name: 'Pera' })

    await screen.findByText('Producto guardado correctamente.')
    await waitFor(() => expect(submitButton).not.toBeDisabled())
    expect(vi.mocked(listAdminProducts).mock.calls.length).toBeGreaterThan(1)
  })

  it('edits selected product and calls update with context id', async () => {
    vi.mocked(listAdminProducts)
      .mockResolvedValueOnce([{ id: 'p-1', name: 'Nuez' }])
      .mockResolvedValueOnce([{ id: 'p-1', name: 'Nuez Premium' }])

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.click(screen.getByRole('button', { name: /Editar/i }))
    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Nuez Premium' } })
    fireEvent.click(screen.getByRole('button', { name: /Actualizar producto/i }))

    await waitFor(() => expect(vi.mocked(updateAdminProduct)).toHaveBeenCalledWith('p-1', { name: 'Nuez Premium' }))
    expect(await screen.findByText('Producto guardado correctamente.')).toBeInTheDocument()
    expect(await screen.findByText('Nuez Premium')).toBeInTheDocument()
    expect(screen.queryByText('Nuez')).not.toBeInTheDocument()
  })

  it('shows error state when create API fails and allows retry reset', async () => {
    vi.mocked(createAdminProduct).mockRejectedValueOnce(new Error('boom'))
    vi.mocked(mapAdminWriteError).mockReturnValueOnce({
      message: 'No se pudo guardar producto.',
      fieldErrors: [{ field: 'name', message: 'El nombre debe ser único.' }],
    })

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Pera' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar producto.')
    expect(screen.getByText('El nombre debe ser único.')).toBeInTheDocument()
    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /Reintentar/i }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })
})
