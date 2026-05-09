import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminCatalogJobsPage } from './AdminCatalogJobsPage'
import {
  awaitAdminImportTerminalStatus,
  createAdminImportJob,
  getAdminCatalogJobReport,
  getAdminCatalogJobRows,
  listAdminCatalogJobs,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminCatalogJobs: vi.fn(async () => [{ id: 'job-1', status: 'FAILED', summary: 'failed', lastError: 'bad' }]),
  getAdminCatalogJobRows: vi.fn(async () => [{ rowNumber: 3, outcome: 'FAILED', errorCode: 'INVALID_SKU', errorMessage: 'SKU inválido', payload: '{"sku":""}' }]),
  getAdminCatalogJobReport: vi.fn(async () => ({ summary: 'processed=3,succeeded=2,failed=1', failedRows: 1, rows: [{ rowNumber: 3, outcome: 'FAILED', errorCode: 'INVALID_SKU', errorMessage: 'SKU inválido', payload: '{"sku":""}' }] })),
  createAdminImportJob: vi.fn(async () => ({ id: 'job-2', status: 'QUEUED' })),
  awaitAdminImportTerminalStatus: vi.fn(async () => ({ id: 'job-2', status: 'COMPLETED' })),
  mapAdminWriteError: vi.fn(() => ({ message: 'Error API' })),
}))

describe('AdminCatalogJobsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows cancel as non-actionable when backend contract is unsupported', async () => {
    vi.mocked(listAdminCatalogJobs).mockResolvedValueOnce([
      {
        id: 'job-1',
        type: 'IMPORT',
        status: 'FAILED',
        createdAt: '2026-01-01T10:00:00Z',
        updatedAt: '2026-01-01T10:02:00Z',
      },
    ])

    render(<AdminCatalogJobsPage />)

    expect(await screen.findByText(/Cancelación no disponible/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cancelar job/i })).toBeDisabled()
    expect(screen.getByText(/SKU inválido/i)).toBeInTheDocument()
    expect(vi.mocked(getAdminCatalogJobRows)).toHaveBeenCalledWith('job-1')
    expect(vi.mocked(getAdminCatalogJobReport)).toHaveBeenCalledWith('job-1')
  })

  it('uploads csv and polls until completed refresh', async () => {
    vi.mocked(awaitAdminImportTerminalStatus).mockImplementationOnce(async (_jobId, options) => {
      options?.onProgress?.({ id: 'job-2', status: 'PROCESSING' })
      await new Promise((resolve) => setTimeout(resolve, 0))
      return { id: 'job-2', status: 'COMPLETED' }
    })

    render(<AdminCatalogJobsPage />)
    await screen.findByText(/Catalog Jobs/i)

    const input = screen.getByLabelText(/Contenido CSV/i)
    fireEvent.change(input, { target: { value: 'name,slug\nPera,pera' } })

    fireEvent.click(screen.getByRole('button', { name: /Subir CSV/i }))

    expect(await screen.findByText(/Estado: QUEUED/i)).toBeInTheDocument()
    expect(await screen.findByText(/Estado: PROCESSING/i)).toBeInTheDocument()
    expect(await screen.findByText(/Estado: COMPLETED/i)).toBeInTheDocument()
    expect(await screen.findByText('Importación completada.')).toBeInTheDocument()
    expect(vi.mocked(createAdminImportJob)).toHaveBeenCalled()
    expect(vi.mocked(awaitAdminImportTerminalStatus)).toHaveBeenCalledWith('job-2', expect.any(Object))
    expect(vi.mocked(listAdminCatalogJobs).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(vi.mocked(getAdminCatalogJobRows).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(vi.mocked(getAdminCatalogJobReport).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('keeps pending state and blocks duplicate upload until terminal status resolves', async () => {
    let resolveTerminal: ((value: { id: string; status: 'COMPLETED' }) => void) | undefined
    vi.mocked(awaitAdminImportTerminalStatus).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveTerminal = resolve
        }),
    )

    render(<AdminCatalogJobsPage />)
    await screen.findByText(/Catalog Jobs/i)

    fireEvent.change(screen.getByLabelText(/Contenido CSV/i), { target: { value: 'name,slug\nPera,pera' } })
    const uploadButton = screen.getByRole('button', { name: /Subir CSV/i })

    fireEvent.click(uploadButton)
    fireEvent.click(uploadButton)

    expect(await screen.findByRole('status')).toHaveTextContent('Guardando cambios…')
    expect(uploadButton).toBeDisabled()
    expect(vi.mocked(createAdminImportJob)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(awaitAdminImportTerminalStatus)).toHaveBeenCalledTimes(1)

    resolveTerminal?.({ id: 'job-2', status: 'COMPLETED' })

    await screen.findByText('Importación completada.')
    await waitFor(() => expect(uploadButton).not.toBeDisabled())
  })

  it('rejects invalid csv input without enqueueing a job', async () => {
    render(<AdminCatalogJobsPage />)
    await screen.findByText(/Catalog Jobs/i)

    fireEvent.change(screen.getByLabelText(/Contenido CSV/i), { target: { value: 'sin separador' } })
    fireEvent.click(screen.getByRole('button', { name: /Subir CSV/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Archivo CSV inválido.')
    expect(vi.mocked(createAdminImportJob)).not.toHaveBeenCalled()
    expect(vi.mocked(awaitAdminImportTerminalStatus)).not.toHaveBeenCalled()
  })

  it('shows terminal failure and keeps diagnostics refreshed', async () => {
    vi.mocked(awaitAdminImportTerminalStatus).mockResolvedValueOnce({
      id: 'job-2',
      status: 'FAILED',
      lastError: 'Fila 3 inválida',
    })

    render(<AdminCatalogJobsPage />)
    await screen.findByText(/Catalog Jobs/i)

    fireEvent.change(screen.getByLabelText(/Contenido CSV/i), { target: { value: 'name,slug\nPera,pera' } })
    fireEvent.click(screen.getByRole('button', { name: /Subir CSV/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Fila 3 inválida')
    expect(vi.mocked(listAdminCatalogJobs).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(vi.mocked(getAdminCatalogJobRows).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(vi.mocked(getAdminCatalogJobReport).mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})
