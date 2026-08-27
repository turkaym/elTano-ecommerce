import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as service from '../services/procurementService'
import { AdminProcurementPage } from './AdminProcurementPage'

describe('AdminProcurementPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows purchases and their progress', async () => {
    vi.spyOn(service, 'listPurchases').mockResolvedValue([{ id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'A-12', status: 'PENDING', progress: '4 / 10' }])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    render(<AdminProcurementPage />)
    expect(await screen.findByText('Quintal')).toBeInTheDocument()
    expect(screen.getByText('4 / 10')).toBeInTheDocument()
  })

  it('shows an actionable loading error', async () => {
    vi.spyOn(service, 'listPurchases').mockRejectedValue(new Error('offline'))
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    render(<AdminProcurementPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load purchases')
  })

  it('provides compact mapping and purchase registration controls', async () => {
    const user = userEvent.setup()
    vi.spyOn(service, 'listPurchases').mockResolvedValue([])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([{ id: 's1', name: 'Quintal', active: true }])
    vi.spyOn(service, 'listMappings').mockResolvedValue([{ id: 'm1', supplierId: 's1', supplierItemCode: 'Q-1', description: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', defaultConversion: '1', active: true }])
    const createMapping = vi.spyOn(service, 'createMapping').mockResolvedValue({ id: 'm2', supplierId: 's1', supplierItemCode: 'Q-2', description: 'Walnut bag', targetType: 'VARIANT_UNIT', variantId: 'v2', defaultConversion: '1', active: true })
    const createPurchase = vi.spyOn(service, 'createPurchase').mockResolvedValue({ id: 'p2', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'B-2', status: 'PENDING' })
    render(<AdminProcurementPage />)

    await user.type(await screen.findByLabelText('Supplier item code'), 'Q-2')
    await user.type(screen.getByLabelText('Mapping description'), 'Walnut bag')
    await user.type(screen.getByLabelText('Target ID'), 'v2')
    await user.click(screen.getByRole('button', { name: 'Add mapping' }))
    expect(createMapping).toHaveBeenCalledWith(expect.objectContaining({ supplierItemCode: 'Q-2', variantId: 'v2' }))

    await user.type(screen.getByLabelText('Document number'), 'B-2')
    await user.type(screen.getByLabelText('Ordered quantity'), '4')
    await user.click(screen.getByRole('button', { name: 'Register purchase' }))
    expect(createPurchase).toHaveBeenCalledWith(expect.objectContaining({ documentNumber: 'B-2' }))
  })

  it('loads purchase detail and refreshes list and detail after reviewed confirmation', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'A-12', status: 'PENDING' as const, progress: '0 / 2' }
    const listPurchases = vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    const getPurchase = vi.spyOn(service, 'getPurchase')
      .mockResolvedValueOnce({ ...purchase, lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
      .mockResolvedValueOnce({ ...purchase, status: 'RECEIVED', progress: '2 / 2', lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
    vi.spyOn(service, 'previewReceipt').mockResolvedValue({ canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 2 }] })
    const confirm = vi.spyOn(service, 'confirmReceipt').mockResolvedValue({ replayed: true, canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 2 }] })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    expect(await screen.findByText('Almond bag: 2 × 1')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Accepted Almond bag'), '2')
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))
    expect(await screen.findByText('VARIANT_UNIT v1: +2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm reviewed receipt' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Receipt was already confirmed.')
    expect(confirm).toHaveBeenCalledWith('p1', expect.objectContaining({ lines: expect.any(Array) }), expect.any(String))
    expect(getPurchase).toHaveBeenCalledTimes(2)
    expect(listPurchases).toHaveBeenCalledTimes(2)
  })

  it('reports detail errors and retries a failed initial refresh', async () => {
    const user = userEvent.setup()
    const listPurchases = vi.spyOn(service, 'listPurchases')
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([{ id: 'p1', supplierName: 'Recovered', documentType: 'Invoice', documentNumber: 'R-1', status: 'PENDING' }])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase').mockRejectedValue(new Error('detail unavailable'))
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: 'Retry' }))
    await user.click(await screen.findByRole('button', { name: /Recovered/i }))

    expect(await screen.findByRole('status')).toHaveTextContent('Could not load purchase detail.')
    expect(listPurchases).toHaveBeenCalledTimes(2)
  })

  it('reuses the reviewed draft idempotency key when confirmation is retried', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'ERR-1', status: 'PENDING' as const }
    vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase').mockResolvedValue({ ...purchase, lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
    vi.spyOn(service, 'previewReceipt').mockResolvedValue({ canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 1 }] })
    const confirm = vi.spyOn(service, 'confirmReceipt')
      .mockRejectedValueOnce(new Error('ambiguous failure'))
      .mockResolvedValueOnce({ replayed: true, canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 1 }] })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    await user.type(await screen.findByLabelText('Accepted Almond bag'), '1')
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm reviewed receipt' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Could not confirm receipt. No stock changes were applied.')
    expect(screen.getByText('VARIANT_UNIT v1: +1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm reviewed receipt' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Confirm reviewed receipt' }))
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(confirm.mock.calls[1][2]).toBe(confirm.mock.calls[0][2])
  })

  it('clears a committed draft even when refreshing purchase data fails', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'COMMIT-1', status: 'PENDING' as const }
    vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase')
      .mockResolvedValueOnce({ ...purchase, lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
      .mockRejectedValueOnce(new Error('refresh unavailable'))
    const previewReceipt = vi.spyOn(service, 'previewReceipt').mockResolvedValue({ canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 1 }] })
    const confirmReceipt = vi.spyOn(service, 'confirmReceipt').mockResolvedValue({ replayed: false, canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 1 }] })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    await user.type(await screen.findByLabelText('Accepted Almond bag'), '1')
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))
    await user.click(await screen.findByRole('button', { name: 'Confirm reviewed receipt' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Receipt confirmed, but refreshed data could not be loaded.')
    expect(screen.queryByText('No stock changes were applied.')).not.toBeInTheDocument()
    expect(screen.queryByText('VARIANT_UNIT v1: +1')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Accepted Almond bag')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Confirm reviewed receipt' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))
    await user.click(screen.getByRole('button', { name: 'Confirm reviewed receipt' }))
    expect(previewReceipt).toHaveBeenCalledTimes(1)
    expect(confirmReceipt).toHaveBeenCalledTimes(1)
  })

  it('invalidates a reviewed preview when receipt inputs change', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'STALE-1', status: 'PENDING' as const }
    vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase').mockResolvedValue({ ...purchase, lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
    vi.spyOn(service, 'previewReceipt').mockResolvedValue({ canonicalDeltas: [{ targetType: 'VARIANT_UNIT', targetId: 'v1', delta: 1 }] })
    const confirm = vi.spyOn(service, 'confirmReceipt')
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    await user.type(screen.getByLabelText('Accepted Almond bag'), '1')
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))
    expect(await screen.findByText('VARIANT_UNIT v1: +1')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Reason / note Almond bag'), 'changed after preview')
    expect(screen.queryByText('VARIANT_UNIT v1: +1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm reviewed receipt' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Confirm reviewed receipt' }))
    expect(confirm).not.toHaveBeenCalled()
  })

  it('builds one reviewed receipt from dispositions entered for every purchase line', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'MULTI-1', status: 'PENDING' as const }
    vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase').mockResolvedValue({ ...purchase, lines: [
      { id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' },
      { id: 'l2', mappingId: 'm2', supplierItemCode: 'Q-2', supplierDescription: 'Walnut bag', targetType: 'VARIANT_UNIT', variantId: 'v2', orderedQuantity: '3', conversion: '1' },
    ] })
    const previewReceipt = vi.spyOn(service, 'previewReceipt').mockResolvedValue({ canonicalDeltas: [] })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    await user.type(await screen.findByLabelText('Accepted Almond bag'), '2')
    await user.type(screen.getByLabelText('Temporarily missing Walnut bag'), '3')
    await user.click(screen.getByRole('button', { name: 'Preview stock delta' }))

    expect(previewReceipt).toHaveBeenCalledWith('p1', { lines: [
      { purchaseLineId: 'l1', dispositions: [{ type: 'ACCEPTED_ORDERED', quantity: '2' }] },
      { purchaseLineId: 'l2', dispositions: [{ type: 'TEMP_MISSING', quantity: '3' }] },
    ] })
  })

  it('updates the editable lines of an unconfirmed pending purchase', async () => {
    const user = userEvent.setup()
    const purchase = { id: 'p1', supplierName: 'Quintal', documentType: 'Invoice', documentNumber: 'EDIT-1', purchasedAt: '2026-08-21', status: 'PENDING' as const }
    vi.spyOn(service, 'listPurchases').mockResolvedValue([purchase])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([])
    vi.spyOn(service, 'listMappings').mockResolvedValue([])
    vi.spyOn(service, 'getPurchase').mockResolvedValue({ ...purchase, lines: [{ id: 'l1', mappingId: 'm1', supplierItemCode: 'Q-1', supplierDescription: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', orderedQuantity: '2', conversion: '1' }] })
    const updatePurchase = vi.spyOn(service, 'updatePurchase').mockResolvedValue({ ...purchase, lines: [] })
    render(<AdminProcurementPage />)

    await user.click(await screen.findByRole('button', { name: /Quintal/i }))
    const quantity = await screen.findByLabelText('Edit ordered quantity Almond bag')
    await user.clear(quantity)
    await user.type(quantity, '4')
    await user.click(screen.getByRole('button', { name: 'Save pending purchase' }))

    expect(updatePurchase).toHaveBeenCalledWith('p1', {
      purchasedAt: '2026-08-21',
      lines: [{ mappingId: 'm1', orderedQuantity: '4', conversion: '1' }],
    })
  })

  it('exposes supplier and mapping activation controls', async () => {
    const user = userEvent.setup()
    vi.spyOn(service, 'listPurchases').mockResolvedValue([])
    vi.spyOn(service, 'listSuppliers').mockResolvedValue([{ id: 's1', name: 'Quintal', active: true }])
    vi.spyOn(service, 'listMappings').mockResolvedValue([{ id: 'm1', supplierId: 's1', supplierItemCode: 'Q-1', description: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', defaultConversion: '1', active: true }])
    const updateSupplier = vi.spyOn(service, 'updateSupplier').mockResolvedValue({ id: 's1', name: 'Quintal', active: false })
    const updateMapping = vi.spyOn(service, 'updateMapping').mockResolvedValue({ id: 'm1', supplierId: 's1', supplierItemCode: 'Q-1', description: 'Almond bag', targetType: 'VARIANT_UNIT', variantId: 'v1', defaultConversion: '1', active: false })
    render(<AdminProcurementPage />)

    const supplierControls = await screen.findByRole('region', { name: 'Supplier controls' })
    await user.click(within(supplierControls).getByRole('button', { name: 'Deactivate' }))
    expect(updateSupplier).toHaveBeenCalledWith('s1', { active: false })

    const mappingControls = screen.getByRole('region', { name: 'Mapping controls' })
    await user.click(within(mappingControls).getByRole('button', { name: 'Deactivate' }))
    expect(updateMapping).toHaveBeenCalledWith('m1', { active: false })
  })
})
