import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/httpClient'
import {
  addPurchaseDraftLine,
  cancelPurchase,
  confirmPurchaseDraft,
  confirmReceipt,
  correctPurchase,
  createManualPurchaseDraft,
  createMapping,
  createPurchase,
  createSupplier,
  deletePurchaseDraftLine,
  downloadPurchaseDraftSource,
  downloadPurchaseDraftTemplate,
  getPurchase,
  getPurchaseDraft,
  importPurchaseWorkbook,
  listCatalogCandidates,
  listMappings,
  listPurchaseDrafts,
  listPurchases,
  matchPurchaseDraftLine,
  previewPurchaseDraft,
  previewReceipt,
  updatePurchaseDraftLine,
  updateMapping,
  updatePurchase,
  updateSupplier,
} from './procurementService'

describe('procurementService', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('lists purchases with filters', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }))
    await listPurchases({ status: 'PENDING', supplierId: 'supplier-1' })
    expect(String(fetchSpy.mock.calls[0][0])).toMatch(/\/api\/admin\/procurement\/purchases\?status=PENDING&supplierId=supplier-1$/)
  })

  it('previews and confirms the same reviewed draft with an idempotency key', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ canonicalDeltas: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ receiptId: 'r1', replayed: false }), { status: 200 }))
    const draft = { lines: [{ purchaseLineId: 'l1', acceptedOrdered: '2.000000' }] }
    await previewReceipt('p1', draft)
    await confirmReceipt('p1', draft, 'key-1')
    expect(fetchSpy.mock.calls[1][1]).toMatchObject({ headers: expect.objectContaining({ 'Idempotency-Key': 'key-1' }) })
  })

  it('creates and updates suppliers with JSON write requests', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ id: 's1', name: 'Quintal', active: true }))
      .mockResolvedValueOnce(jsonResponse({ id: 's1', name: 'Quintal', active: false }))

    await createSupplier({ name: 'Quintal', taxIdentity: '20-1' })
    await updateSupplier('s1', { active: false })

    expect(fetchSpy.mock.calls[0]).toEqual(expect.arrayContaining([expect.stringMatching(/\/suppliers$/), expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: 'Quintal', taxIdentity: '20-1' }) })]))
    expect(fetchSpy.mock.calls[1]).toEqual(expect.arrayContaining([expect.stringMatching(/\/suppliers\/s1$/), expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ active: false }) })]))
  })

  it('lists, creates, and updates supplier mappings', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 'm1' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'm1', active: false }))

    await listMappings('supplier/one')
    await createMapping({ supplierId: 's1', supplierItemCode: 'Q-1' })
    await updateMapping('m1', { active: false })

    expect(String(fetchSpy.mock.calls[0][0])).toMatch(/\/mappings\?supplierId=supplier%2Fone$/)
    expect(fetchSpy.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ supplierId: 's1', supplierItemCode: 'Q-1' }) })
    expect(fetchSpy.mock.calls[2][1]).toMatchObject({ method: 'PATCH', body: JSON.stringify({ active: false }) })
  })

  it('gets, creates, and updates purchase evidence', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ id: 'p1' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'p1' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'p1' }))
    const payload = { supplierId: 's1', lines: [{ mappingId: 'm1', orderedQuantity: '2' }] }

    await getPurchase('p1')
    await createPurchase(payload)
    await updatePurchase('p1', payload)

    expect(String(fetchSpy.mock.calls[0][0])).toMatch(/\/purchases\/p1$/)
    expect(fetchSpy.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify(payload) })
    expect(fetchSpy.mock.calls[2][1]).toMatchObject({ method: 'PUT', body: JSON.stringify(payload) })
  })

  it('sends correction and cancellation idempotency evidence', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ canonicalDeltas: [] }))
      .mockResolvedValueOnce(jsonResponse({ canonicalDeltas: [] }))

    await correctPurchase('p1', { reason: 'Damaged', deltas: [] }, 'correction-key')
    await cancelPurchase('p1', 'Duplicate document', 'cancel-key')

    expect(fetchSpy.mock.calls[0][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ reason: 'Damaged', deltas: [] }), headers: expect.objectContaining({ 'Idempotency-Key': 'correction-key' }) })
    expect(fetchSpy.mock.calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ reason: 'Duplicate document' }), headers: expect.objectContaining({ 'Idempotency-Key': 'cancel-key' }) })
  })

  it('preserves structured API error details', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'Duplicate document', code: 'DUPLICATE_DOCUMENT', correlationId: 'corr-1', fieldErrors: [{ field: 'documentNumber', message: 'Already used' }] }), { status: 409, headers: { 'Content-Type': 'application/json' } }))

    const error = await createPurchase({}).catch((failure: unknown) => failure)

    expect(error).toBeInstanceOf(ApiClientError)
    expect(error).toMatchObject({ status: 409, message: 'Duplicate document', code: 'DUPLICATE_DOCUMENT', correlationId: 'corr-1', fieldErrors: [{ field: 'documentNumber', message: 'Already used' }] })
  })

  it('falls back to the HTTP status when an API error is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('upstream unavailable', { status: 503 }))

    await expect(listPurchases()).rejects.toMatchObject({ status: 503, message: 'Request failed with status 503' })
  })

  it('uploads XLSX as FormData without setting Content-Type and preserves the idempotency key', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ id: 'd1', version: 0, lines: [] }, 201))
    const file = new File(['xlsx'], 'compra.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

    await importPurchaseWorkbook('s1', file, 'upload-key')

    const options = fetchSpy.mock.calls[0][1] as RequestInit
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.headers).toEqual(expect.objectContaining({ 'Idempotency-Key': 'upload-key' }))
    expect(options.headers).not.toHaveProperty('Content-Type')
  })

  it('implements draft CRUD, matching, preview and confirmation contracts', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 0, lines: [] }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 1, lines: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 2, lines: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 3, lines: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 4, lines: [] }))
      .mockResolvedValueOnce(jsonResponse([{ value: 'v1', label: 'Almendras - ALM-1', targetType: 'VARIANT_UNIT' }]))
      .mockResolvedValueOnce(jsonResponse({ id: 'd1', version: 5, lines: [] }))
      .mockResolvedValueOnce(jsonResponse({ version: 6, ready: true, previewHash: 'hash', canonicalDeltas: [], errors: [] }))
      .mockResolvedValueOnce(jsonResponse({ draftId: 'd1', purchaseId: 'p1', receiptId: 'r1', replayed: false, canonicalDeltas: [] }))

    await listPurchaseDrafts()
    await createManualPurchaseDraft({ supplierId: 's1', purchaseDate: '2026-08-29', lines: [] })
    await addPurchaseDraftLine('d1', { version: 0, productName: 'Almendras', quantity: '2', unit: 'UNIDAD' })
    await updatePurchaseDraftLine('d1', 'l1', { version: 1, productName: 'Almendras', quantity: '3', unit: 'UNIDAD' })
    await deletePurchaseDraftLine('d1', 'l1', 2)
    await getPurchaseDraft('d1')
    await listCatalogCandidates('alm', 'UNIDAD', 10)
    await matchPurchaseDraftLine('d1', 'l1', { version: 4, targetId: 'v1', remember: true })
    await previewPurchaseDraft('d1', 5)
    await confirmPurchaseDraft('d1', { version: 6, previewHash: 'hash' }, 'confirm-key')

    expect(String(fetchSpy.mock.calls[6][0])).toContain('catalog-candidates?q=alm&unit=UNIDAD&limit=10')
    expect(fetchSpy.mock.calls[7][1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ version: 4, targetId: 'v1', remember: true }) })
    expect(fetchSpy.mock.calls[9][1]).toMatchObject({ headers: expect.objectContaining({ 'Idempotency-Key': 'confirm-key' }) })
  })

  it('downloads template and original source while preserving the server filename', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('template', { status: 200 }))
      .mockResolvedValueOnce(new Response('source', { status: 200, headers: { 'Content-Disposition': "attachment; filename*=UTF-8''compra%20agosto.xlsx" } }))

    const template = await downloadPurchaseDraftTemplate()
    const source = await downloadPurchaseDraftSource('d1')

    expect(template).toBeInstanceOf(Blob)
    expect(template.size).toBeGreaterThan(0)
    expect(source.filename).toBe('compra agosto.xlsx')
    expect(String(fetchSpy.mock.calls[1][0])).toMatch(/purchase-drafts\/d1\/source-file$/)
  })
})

function jsonResponse(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } })
}
