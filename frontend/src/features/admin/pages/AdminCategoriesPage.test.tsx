import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminCategoriesPage } from './AdminCategoriesPage'
import {
  createAdminCategory,
  listAdminCategories,
  listAdminProducts,
  mapAdminWriteError,
  updateAdminCategory,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminCategories: vi.fn(async () => [
    { id: 'c-1', name: 'Secos', slug: 'secos', active: true },
    { id: 'c-2', name: 'Frutas', slug: 'frutas', active: false },
  ]),
  listAdminProducts: vi.fn(async () => [{ id: 'p-1', name: 'Nuez', categoryId: 'c-1', active: true }]),
  createAdminCategory: vi.fn(async () => ({ id: 'c-2', name: 'Frutas', slug: 'frutas' })),
  updateAdminCategory: vi.fn(async () => ({ id: 'c-1', name: 'Secos Premium', slug: 'secos-premium' })),
  mapAdminWriteError: vi.fn(() => ({ message: 'Error API' })),
}))

describe('AdminCategoriesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('organizes the category editor, filters and status cards into accessible sections', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    expect(screen.getByRole('region', { name: /Datos de categoría/i })).toBeInTheDocument()
    const filters = screen.getByRole('region', { name: /Filtros de categorías/i })
    expect(within(filters).getByLabelText(/Filtrar categorías por estado/i)).toBeInTheDocument()

    expect(screen.getByRole('article', { name: /Categoría Secos/i })).toHaveTextContent('Estado: Activa')
    expect(screen.getByRole('article', { name: /Categoría Frutas/i })).toHaveTextContent('Estado: Inactiva')
    expect(screen.getByRole('button', { name: /Desactivar categoría Secos/i })).toBeInTheDocument()
  })

  it('creates category and refreshes list', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')
    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas Ácidas' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear categoría/i }))
    await waitFor(() =>
      expect(vi.mocked(createAdminCategory)).toHaveBeenCalledWith({ name: 'Frutas Ácidas', slug: 'frutas-acidas', active: true }),
    )
    expect(await screen.findByText('Categoría guardada correctamente.')).toBeInTheDocument()
    expect(vi.mocked(listAdminCategories).mock.calls.length).toBeGreaterThan(1)
  })

  it('auto-generates category slug from name until the slug is manually overridden', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas Ácidas' } })
    expect(screen.getByLabelText(/Slug categoría/i)).toHaveValue('frutas-acidas')

    fireEvent.change(screen.getByLabelText(/Slug categoría/i), { target: { value: 'citrus' } })
    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Frutas Nuevas' } })

    expect(screen.getByLabelText(/Slug categoría/i)).toHaveValue('citrus')
  })

  it('keeps the category form usable when the category list is empty', async () => {
    vi.mocked(listAdminCategories).mockResolvedValueOnce([])

    render(<AdminCategoriesPage />)

    expect(await screen.findByRole('button', { name: /Crear categoría/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/Nombre categoría/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Slug categoría/i)).toBeInTheDocument()
    expect(screen.getByText('Sin categorías')).toBeInTheDocument()
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
    fireEvent.change(screen.getByLabelText(/Slug categoría/i), { target: { value: 'frutas' } })
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
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos', slug: 'secos', active: true }])
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos Premium', slug: 'secos-premium', active: true }])

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')
    fireEvent.click(screen.getByRole('button', { name: /Editar/i }))
    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Secos Premium' } })
    fireEvent.change(screen.getByLabelText(/Slug categoría/i), { target: { value: 'secos-premium' } })
    fireEvent.click(screen.getByRole('button', { name: /Actualizar categoría/i }))
    await waitFor(() =>
      expect(vi.mocked(updateAdminCategory)).toHaveBeenCalledWith('c-1', {
        name: 'Secos Premium',
        slug: 'secos-premium',
        active: true,
      }),
    )
    expect(await screen.findByText('Categoría guardada correctamente.')).toBeInTheDocument()
    expect(await screen.findByText('Secos Premium')).toBeInTheDocument()
    expect(screen.queryByText('Secos')).not.toBeInTheDocument()
  })

  it('loads an existing category slug for edit and preserves it when the name changes', async () => {
    vi.mocked(listAdminCategories).mockResolvedValueOnce([{ id: 'c-1', name: 'Secos', slug: 'secos-especiales', active: true }])

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    fireEvent.click(screen.getByRole('button', { name: /Editar/i }))
    expect(screen.getByLabelText(/Slug categoría/i)).toHaveValue('secos-especiales')

    fireEvent.change(screen.getByLabelText(/Nombre categoría/i), { target: { value: 'Secos Premium' } })
    expect(screen.getByLabelText(/Slug categoría/i)).toHaveValue('secos-especiales')
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
    fireEvent.change(screen.getByLabelText(/Slug categoría/i), { target: { value: 'frutas' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear categoría/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar categoría.')
    expect(screen.getByText('Nombre: La categoría ya existe.')).toBeInTheDocument()
    expect(vi.mocked(listAdminCategories)).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /Reintentar/i }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('shows category status and filters active/inactive categories', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    expect(screen.getByText('Estado: Activa')).toBeInTheDocument()
    expect(screen.getByText('Estado: Inactiva')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar categorías por estado/i), { target: { value: 'inactive' } })
    expect(screen.queryByText('Secos')).not.toBeInTheDocument()
    expect(screen.getByText('Frutas')).toBeInTheDocument()
  })

  it('blocks category deactivation when active products still belong to it', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')

    fireEvent.click(screen.getByRole('button', { name: /Desactivar categoría Secos/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se puede desactivar la categoría porque tiene 1 producto activo asociado.',
    )
    expect(vi.mocked(updateAdminCategory)).not.toHaveBeenCalled()
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('confirms and deactivates categories without active products', async () => {
    vi.mocked(listAdminProducts).mockResolvedValueOnce([])
    vi.mocked(listAdminCategories)
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos', slug: 'secos', active: true }])
      .mockResolvedValueOnce([{ id: 'c-1', name: 'Secos', slug: 'secos', active: false }])

    render(<AdminCategoriesPage />)
    await screen.findByText('Secos')
    fireEvent.click(screen.getByRole('button', { name: /Desactivar categoría Secos/i }))

    await waitFor(() =>
      expect(vi.mocked(updateAdminCategory)).toHaveBeenCalledWith('c-1', {
        name: 'Secos',
        slug: 'secos',
        active: false,
      }),
    )
    expect(await screen.findByText('Categoría desactivada correctamente.')).toBeInTheDocument()
  })

  it('reactivates inactive categories with confirmation', async () => {
    render(<AdminCategoriesPage />)
    await screen.findByText('Frutas')

    fireEvent.click(screen.getByRole('button', { name: /Reactivar categoría Frutas/i }))

    await waitFor(() =>
      expect(vi.mocked(updateAdminCategory)).toHaveBeenCalledWith('c-2', {
        name: 'Frutas',
        slug: 'frutas',
        active: true,
      }),
    )
    expect(await screen.findByText('Categoría reactivada correctamente.')).toBeInTheDocument()
  })
})
