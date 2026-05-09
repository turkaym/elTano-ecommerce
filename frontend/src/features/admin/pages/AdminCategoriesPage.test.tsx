import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminCategoriesPage } from './AdminCategoriesPage'
import {
  createAdminCategory,
  listAdminCategories,
  mapAdminWriteError,
  updateAdminCategory,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminCategories: vi.fn(async () => [{ id: 'c-1', name: 'Secos', slug: 'secos' }]),
  createAdminCategory: vi.fn(async () => ({ id: 'c-2', name: 'Frutas', slug: 'frutas' })),
  updateAdminCategory: vi.fn(async () => ({ id: 'c-1', name: 'Secos Premium', slug: 'secos-premium' })),
  mapAdminWriteError: vi.fn(() => ({ message: 'Error API' })),
}))

describe('AdminCategoriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('creates category and refreshes list', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')
    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear categoría/i }))
    await waitFor(() => expect(vi.mocked(createAdminCategory)).toHaveBeenCalledWith({ name: 'Frutas' }))
    expect(await screen.findByText('Categoría guardada correctamente.')).toBeInTheDocument()
    expect(vi.mocked(listAdminCategories).mock.calls.length).toBeGreaterThan(1)
  })

  it('keeps pending state and blocks duplicate submit until create resolves', async () => {
    let resolveCreate: ((value: { id: string; name: string; slug: string }) => void) | undefined
    vi.mocked(createAdminCategory).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveCreate = resolve
        }),
    )

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas' } })
    const submitButton = screen.getByRole('button', { name: /Crear categoría/i })
    fireEvent.click(submitButton)
    fireEvent.click(submitButton)

    expect(await screen.findByRole('status')).toHaveTextContent('Guardando cambios…')
    expect(submitButton).toBeDisabled()
    expect(vi.mocked(createAdminCategory)).toHaveBeenCalledTimes(1)

    resolveCreate?.({ id: 'c-2', name: 'Frutas', slug: 'frutas' })

    await screen.findByText('Categoría guardada correctamente.')
    await waitFor(() => expect(submitButton).not.toBeDisabled())
    expect(vi.mocked(listAdminCategories).mock.calls.length).toBeGreaterThan(1)
  })

  it('updates category after selecting edit', async () => {
    vi.mocked(listAdminCategories)
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos', slug: 'secos' }])
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos Premium', slug: 'secos-premium' }])

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')
    fireEvent.click(screen.getByRole('button', { name: /Editar/i }))
    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Secos Premium' } })
    fireEvent.click(screen.getByRole('button', { name: /Actualizar categoría/i }))
    await waitFor(() => expect(vi.mocked(updateAdminCategory)).toHaveBeenCalledWith('c-1', { name: 'Secos Premium' }))
    expect(await screen.findByText('Categoría guardada correctamente.')).toBeInTheDocument()
    expect(await screen.findByText('Secos Premium')).toBeInTheDocument()
    expect(screen.queryByText('Secos')).not.toBeInTheDocument()
  })

  it('shows API error and clears it on retry action', async () => {
    vi.mocked(createAdminCategory).mockRejectedValueOnce(new Error('boom'))
    vi.mocked(mapAdminWriteError).mockReturnValueOnce({
      message: 'No se pudo guardar categoría.',
      fieldErrors: [{ field: 'name', message: 'La categoría ya existe.' }],
    })

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear categoría/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar categoría.')
    expect(screen.getByText('La categoría ya existe.')).toBeInTheDocument()
    expect(vi.mocked(listAdminCategories)).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /Reintentar/i }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })
})
