import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/httpClient'
import {
  confirmPurchaseDraft,
  createManualPurchaseDraft,
  downloadPurchaseDraftSource,
  downloadPurchaseDraftTemplate,
  importPurchaseWorkbook,
  getPurchaseDraft,
  listCatalogCandidates,
  listMappings,
  listPurchaseDrafts,
  listPurchases,
  listSuppliers,
  matchPurchaseDraftLine,
  previewPurchaseDraft,
  repairMapping,
} from '../services/procurementService'
import { AdminProcurementPage } from './AdminProcurementPage'

vi.mock('../services/procurementService', () => ({
  addPurchaseDraftLine: vi.fn(),
  confirmPurchaseDraft: vi.fn(),
  createManualPurchaseDraft: vi.fn(),
  createSupplier: vi.fn(),
  deletePurchaseDraftLine: vi.fn(),
  downloadPurchaseDraftSource: vi.fn(),
  downloadPurchaseDraftTemplate: vi.fn(),
  getPurchaseDraft: vi.fn(),
  importPurchaseWorkbook: vi.fn(),
  listCatalogCandidates: vi.fn(),
  listMappings: vi.fn(),
  listPurchaseDrafts: vi.fn(),
  listPurchases: vi.fn(),
  listSuppliers: vi.fn(),
  matchPurchaseDraftLine: vi.fn(),
  previewPurchaseDraft: vi.fn(),
  repairMapping: vi.fn(),
  updatePurchaseDraftLine: vi.fn(),
}))

const supplier = { id: 's1', name: 'Proveedor Norte', active: true }
const unresolvedDraft = {
  id: 'd1', version: 2, status: 'DRAFT' as const, supplierId: 's1', supplierName: supplier.name,
  purchaseDate: '2026-08-29', sourceType: 'XLSX' as const, originalFilename: 'compra.xlsx', sourceSha256: 'hash',
  previewHash: null, confirmedPurchaseId: null, confirmedReceiptId: null, reused: false,
  lines: [{ id: 'l1', rowNumber: 2, sourceDate: '2026-08-29', productName: 'Almendra', sourceQuantity: '2', quantity: '2', unit: 'UNIDAD' as const, errors: [], matchStatus: 'UNRESOLVED' as const, targetType: null, productId: null, variantId: null, targetLabel: null, targetLabelPersisted: false, conversion: null, canonicalDelta: null }],
}
const readyDraft = {
  ...unresolvedDraft, version: 3,
  lines: [{ ...unresolvedDraft.lines[0], matchStatus: 'MATCHED' as const, targetType: 'VARIANT_UNIT' as const, variantId: 'v1', targetLabel: 'Almendras - ALM-1', targetLabelPersisted: true, conversion: '1', canonicalDelta: 2 }],
}

describe('AdminProcurementPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.mocked(listSuppliers).mockResolvedValue([supplier])
    vi.mocked(listPurchases).mockResolvedValue([])
    vi.mocked(listPurchaseDrafts).mockResolvedValue([])
    vi.mocked(listMappings).mockResolvedValue([])
    vi.mocked(downloadPurchaseDraftTemplate).mockResolvedValue(new Blob(['template']))
    vi.mocked(downloadPurchaseDraftSource).mockResolvedValue({ blob: new Blob(['source']), filename: 'compra.xlsx' })
    vi.mocked(listCatalogCandidates).mockResolvedValue([{ value: 'v1', label: 'Almendras - unidad', targetType: 'VARIANT_UNIT' }])
  })

  it('presents the supplier-first Excel workflow and downloads the official template', async () => {
    const user = userEvent.setup()
    render(<AdminProcurementPage />)

    expect(await screen.findByRole('heading', { name: 'Registrar compra' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Importar Excel' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Carga manual' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Descargar plantilla oficial' }))
    expect(downloadPurchaseDraftTemplate).toHaveBeenCalledOnce()
  })

  it('shows exact source to target and keeps remembered mappings opt-in', async () => {
    const user = userEvent.setup()
    vi.mocked(importPurchaseWorkbook).mockResolvedValue(unresolvedDraft)
    vi.mocked(matchPurchaseDraftLine).mockResolvedValue(readyDraft)
    render(<AdminProcurementPage />)
    await screen.findByRole('option', { name: supplier.name })

    await user.upload(screen.getByLabelText('Archivo Excel'), new File(['xlsx'], 'compra.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }))
    await user.click(screen.getByRole('button', { name: 'Importar y revisar' }))
    await screen.findByRole('heading', { name: supplier.name })
    expect(screen.getByLabelText('Resumen de filas')).toHaveTextContent('1sin resolver')

    await user.type(screen.getByLabelText('Buscar producto para Almendra'), 'alm')
    expect(await screen.findByRole('option', { name: 'Almendras - unidad' })).toBeInTheDocument()
    expect(screen.getByLabelText('Recordar equivalencia para Almendra')).not.toBeChecked()
    await user.selectOptions(screen.getByLabelText('Resultado para Almendra'), 'v1')
    await user.click(screen.getByRole('button', { name: 'Vincular Almendra' }))
    expect(matchPurchaseDraftLine).toHaveBeenCalledWith('d1', 'l1', { version: 2, targetId: 'v1', remember: false })
    expect(await screen.findByText(/Almendra →/)).toHaveTextContent('Almendras - ALM-1')
  })

  it('creates a manual draft without exposing UUID inputs', async () => {
    const user = userEvent.setup()
    vi.mocked(createManualPurchaseDraft).mockResolvedValue(readyDraft)
    render(<AdminProcurementPage />)
    await screen.findByRole('option', { name: supplier.name })
    await user.click(screen.getByRole('tab', { name: 'Carga manual' }))
    await user.type(screen.getByLabelText('Producto'), 'Almendra')
    await user.clear(screen.getByLabelText('Cantidad'))
    await user.type(screen.getByLabelText('Cantidad'), '2')
    await user.click(screen.getByRole('button', { name: 'Crear borrador' }))

    expect(createManualPurchaseDraft).toHaveBeenCalledWith(expect.objectContaining({ supplierId: 's1', lines: [expect.objectContaining({ productName: 'Almendra', quantity: '2' })] }))
    expect(screen.queryByLabelText(/UUID/i)).not.toBeInTheDocument()
  })

  it('blocks preview for invalid and duplicate rows and lets the original source be downloaded', async () => {
    const user = userEvent.setup()
    vi.mocked(importPurchaseWorkbook).mockResolvedValue({
      ...unresolvedDraft,
      lines: [
        { ...unresolvedDraft.lines[0], errors: ['La fila está duplicada exactamente dentro del archivo.'], matchStatus: 'INVALID' },
        { ...unresolvedDraft.lines[0], id: 'l2', rowNumber: 3, productName: 'Nuez', errors: ['La cantidad debe ser mayor que cero.'], matchStatus: 'INVALID' },
      ],
    })
    render(<AdminProcurementPage />)
    await screen.findByRole('option', { name: supplier.name })
    await user.upload(screen.getByLabelText('Archivo Excel'), new File(['x'], 'compra.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Importar y revisar' }))

    const summary = await screen.findByLabelText('Resumen de filas')
    expect(summary).toHaveTextContent('1inválidas')
    expect(summary).toHaveTextContent('1duplicadas')
    expect(screen.getByRole('button', { name: 'Generar vista previa' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Descargar archivo original' }))
    expect(downloadPurchaseDraftSource).toHaveBeenCalledWith('d1')
  })

  it('keeps one confirmation idempotency key across a failed retry and shows receipt evidence', async () => {
    const user = userEvent.setup()
    vi.mocked(importPurchaseWorkbook).mockResolvedValue(readyDraft)
    vi.mocked(previewPurchaseDraft).mockResolvedValue({ version: 3, ready: true, previewHash: 'hash', canonicalDeltas: [{ lineId: 'l1', targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 2 }], errors: [] })
    vi.mocked(confirmPurchaseDraft)
      .mockRejectedValueOnce(new ApiClientError(503, 'Servicio temporalmente no disponible'))
      .mockResolvedValueOnce({ draftId: 'd1', purchaseId: 'p1', receiptId: 'r1', replayed: true, canonicalDeltas: [{ lineId: 'l1', targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 2 }] })
    render(<AdminProcurementPage />)
    await screen.findByRole('option', { name: supplier.name })
    await user.upload(screen.getByLabelText('Archivo Excel'), new File(['x'], 'compra.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Importar y revisar' }))
    await user.click(await screen.findByRole('button', { name: 'Generar vista previa' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar compra' }))
    await user.click(await screen.findByRole('button', { name: 'Reintentar confirmación' }))

    expect(confirmPurchaseDraft).toHaveBeenCalledTimes(2)
    expect(vi.mocked(confirmPurchaseDraft).mock.calls[0][2]).toBe(vi.mocked(confirmPurchaseDraft).mock.calls[1][2])
    expect(await screen.findByText('Compra confirmada')).toBeInTheDocument()
    expect(screen.getByText('Comprobante de recepción registrado')).toBeInTheDocument()
    expect(screen.getByText(/no se duplicó el stock/i)).toBeInTheDocument()
    expect(listPurchases).toHaveBeenCalledTimes(2)
  })

  it('opens confirmed drafts as read-only evidence with purchase and receipt IDs', async () => {
    const user = userEvent.setup()
    const confirmed = { ...readyDraft, status: 'CONFIRMED' as const, confirmedPurchaseId: 'p1', confirmedReceiptId: 'r1', lines: [{ ...readyDraft.lines[0], targetLabelPersisted: false }] }
    vi.mocked(listPurchaseDrafts).mockResolvedValue([confirmed])
    vi.mocked(getPurchaseDraft).mockResolvedValue(confirmed)
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: 'Abrir revisión' }))

    expect(await screen.findByText('Compra confirmada')).toBeInTheDocument()
    expect(screen.getByText('p1')).toBeInTheDocument()
    expect(screen.getByText('r1')).toBeInTheDocument()
    expect(screen.getByText(/Almendra →/)).toHaveTextContent('Almendras - ALM-1')
    expect(screen.getByText(/reconstruida del catálogo actual/i)).toBeInTheDocument()
    expect(screen.getByText('+2')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Generar vista previa' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Eliminar' })).not.toBeInTheDocument()
  })

  it('repairs and reactivates an inactive mapping for future imports', async () => {
    const user = userEvent.setup()
    const mapping = { id: 'm1', supplierId: 's1', supplierItemCode: 'Mariposa 20kg', supplierItemName: 'Mariposa 20kg', description: 'Mariposa 20kg', targetType: 'BULK_GRAM' as const, productId: 'old', variantId: null, targetLabel: 'NUEZ PARTIDA', defaultConversion: '1000', active: false }
    vi.mocked(listMappings).mockResolvedValueOnce([mapping]).mockResolvedValueOnce([{ ...mapping, productId: 'new', targetLabel: 'NUEZ MARIPOSA', active: true }])
    vi.mocked(listCatalogCandidates).mockResolvedValue([{ value: 'new', label: 'NUEZ MARIPOSA (a granel)', targetType: 'BULK_GRAM' }])
    vi.mocked(repairMapping).mockResolvedValue({ ...mapping, productId: 'new', targetLabel: 'NUEZ MARIPOSA', active: true })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByText('Equivalencias de proveedor'))
    expect(screen.getByText(/Mariposa 20kg → NUEZ PARTIDA/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Corregir equivalencia' }))
    await user.type(screen.getByLabelText('Buscar nuevo destino para Mariposa 20kg'), 'mari')
    await user.selectOptions(await screen.findByLabelText('Destino para Mariposa 20kg'), 'new')
    await user.click(screen.getByLabelText('Equivalencia activa para Mariposa 20kg'))
    await user.click(screen.getByRole('button', { name: 'Guardar corrección' }))

    expect(repairMapping).toHaveBeenCalledWith('m1', { targetType: 'BULK_GRAM', productId: 'new', variantId: null, defaultConversion: '1000', active: true })
    expect(await screen.findByText(/Mariposa 20kg → NUEZ MARIPOSA/)).toBeInTheDocument()
  })

  it('distinguishes an expired session from ordinary loading errors', async () => {
    vi.mocked(listSuppliers).mockRejectedValue(new ApiClientError(401, 'Unauthorized'))
    render(<AdminProcurementPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Tu sesión venció')
    await waitFor(() => expect(listPurchaseDrafts).toHaveBeenCalledOnce())
  })
})
