import { type FormEvent, useEffect, useRef, useState } from 'react'
import {
  confirmReceipt,
  createMapping,
  createPurchase,
  createSupplier,
  getPurchase,
  listPurchases,
  listMappings,
  listSuppliers,
  previewReceipt,
  updateMapping,
  updatePurchase,
  updateSupplier,
  type PurchaseDto,
  type MappingDto,
  type ReceiptDraft,
  type ReceiptResult,
  type SupplierDto,
} from '../services/procurementService'
import { AdminEmptyState, AdminLoadingState } from './AdminPageStates'

type ReceiptInputs = { accepted: string; temporaryMissing: string; rejected: string; notDeliverable: string; excess: string; note: string }
type EditableLine = { orderedQuantity: string; conversion: string }

const emptyReceipt = (): ReceiptInputs => ({ accepted: '', temporaryMissing: '', rejected: '', notDeliverable: '', excess: '', note: '' })

export function AdminProcurementPage() {
  const [purchases, setPurchases] = useState<PurchaseDto[]>([])
  const [suppliers, setSuppliers] = useState<SupplierDto[]>([])
  const [mappings, setMappings] = useState<MappingDto[]>([])
  const [selected, setSelected] = useState<PurchaseDto | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [supplierName, setSupplierName] = useState('')
  const [mapping, setMapping] = useState({ supplierId: '', supplierItemCode: '', description: '', targetType: 'VARIANT_UNIT', targetId: '', conversion: '1' })
  const [purchase, setPurchase] = useState({ supplierId: '', documentType: 'Invoice', documentNumber: '', mappingId: '', orderedQuantity: '', conversion: '' })
  const [receipts, setReceipts] = useState<Record<string, ReceiptInputs>>({})
  const [editableLines, setEditableLines] = useState<Record<string, EditableLine>>({})
  const [preview, setPreview] = useState<ReceiptResult | null>(null)
  const confirmationKey = useRef<string | null>(null)
  const [feedback, setFeedback] = useState('')

  async function refresh() {
    setStatus('loading')
    try {
      const [purchaseData, supplierData, mappingData] = await Promise.all([listPurchases(), listSuppliers(), listMappings()])
      setPurchases(purchaseData)
      setSuppliers(supplierData)
      setMappings(mappingData)
      setMapping((current) => ({ ...current, supplierId: current.supplierId || supplierData.find((item) => item.active)?.id || '' }))
      setPurchase((current) => ({ ...current, supplierId: current.supplierId || supplierData.find((item) => item.active)?.id || '', mappingId: current.mappingId || mappingData.find((item) => item.active)?.id || '' }))
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }

  useEffect(() => {
    let active = true
    void Promise.all([listPurchases(), listSuppliers(), listMappings()])
      .then(([purchaseData, supplierData, mappingData]) => {
        if (!active) return
        setPurchases(purchaseData)
        setSuppliers(supplierData)
        setMappings(mappingData)
        setMapping((current) => ({ ...current, supplierId: current.supplierId || supplierData.find((item) => item.active)?.id || '' }))
        setPurchase((current) => ({ ...current, supplierId: current.supplierId || supplierData.find((item) => item.active)?.id || '', mappingId: current.mappingId || mappingData.find((item) => item.active)?.id || '' }))
        setStatus('ready')
      })
      .catch(() => { if (active) setStatus('error') })
    return () => { active = false }
  }, [])

  async function selectPurchase(id: string) {
    try {
      const detail = await getPurchase(id)
      setSelected(detail)
      setReceipts(Object.fromEntries((detail.lines ?? []).map((line) => [line.id, emptyReceipt()])))
      setEditableLines(Object.fromEntries((detail.lines ?? []).map((line) => [line.id, { orderedQuantity: String(line.orderedQuantity), conversion: String(line.conversion) }])))
      setPreview(null)
      confirmationKey.current = null
      setFeedback('')
    }
    catch { setFeedback('Could not load purchase detail.') }
  }

  async function submitSupplier(event: FormEvent) {
    event.preventDefault()
    if (!supplierName.trim()) return
    try { await createSupplier({ name: supplierName.trim() }); setSupplierName(''); await refresh() }
    catch { setFeedback('Could not create supplier.') }
  }

  async function submitMapping(event: FormEvent) {
    event.preventDefault()
    if (!mapping.supplierId || !mapping.supplierItemCode.trim() || !mapping.description.trim() || !mapping.targetId.trim()) return
    try {
      await createMapping({
        supplierId: mapping.supplierId,
        supplierItemCode: mapping.supplierItemCode.trim(),
        description: mapping.description.trim(),
        targetType: mapping.targetType,
        ...(mapping.targetType === 'VARIANT_UNIT' ? { variantId: mapping.targetId.trim() } : { productId: mapping.targetId.trim() }),
        defaultConversion: mapping.conversion,
      })
      setMapping((current) => ({ ...current, supplierItemCode: '', description: '', targetId: '' }))
      setMappings(await listMappings())
    } catch { setFeedback('Could not create mapping.') }
  }

  async function submitPurchase(event: FormEvent) {
    event.preventDefault()
    if (!purchase.supplierId || !purchase.mappingId || !purchase.documentNumber.trim() || !purchase.orderedQuantity) return
    try {
      await createPurchase({
        supplierId: purchase.supplierId,
        documentType: purchase.documentType,
        documentNumber: purchase.documentNumber.trim(),
        purchasedAt: new Date().toISOString().slice(0, 10),
        lines: [{ mappingId: purchase.mappingId, orderedQuantity: purchase.orderedQuantity, conversion: purchase.conversion || undefined }],
      })
      setPurchase((current) => ({ ...current, documentNumber: '', orderedQuantity: '' }))
      setPurchases(await listPurchases())
    } catch { setFeedback('Could not register purchase.') }
  }

  async function toggleSupplier(supplier: SupplierDto) {
    try { await updateSupplier(supplier.id, { active: !supplier.active }); await refresh() }
    catch { setFeedback('Could not update supplier status.') }
  }

  async function toggleMapping(item: MappingDto) {
    try { await updateMapping(item.id, { active: !item.active }); setMappings(await listMappings()) }
    catch { setFeedback('Could not update mapping status.') }
  }

  async function savePendingPurchase() {
    if (!selected || selected.status !== 'PENDING' || !selected.lines?.length) return
    try {
      const updated = await updatePurchase(selected.id, {
        purchasedAt: selected.purchasedAt,
        lines: selected.lines.map((line) => ({
          mappingId: line.mappingId,
          orderedQuantity: editableLines[line.id]?.orderedQuantity ?? String(line.orderedQuantity),
          conversion: editableLines[line.id]?.conversion ?? String(line.conversion),
        })),
      })
      setSelected(updated)
      setPurchases(await listPurchases())
      setFeedback('Pending purchase updated.')
    } catch { setFeedback('Could not update pending purchase.') }
  }

  function draft(): ReceiptDraft | null {
    if (!selected?.lines?.length) return null
    const lines = selected.lines.map((line) => {
      const receipt = receipts[line.id] ?? emptyReceipt()
      const candidates: Array<{ type: string; quantity: string; note?: string }> = [
        { type: 'ACCEPTED_ORDERED', quantity: receipt.accepted },
        { type: 'TEMP_MISSING', quantity: receipt.temporaryMissing },
        { type: 'REJECTED_FINAL', quantity: receipt.rejected, note: receipt.note || undefined },
        { type: 'NOT_DELIVERABLE_FINAL', quantity: receipt.notDeliverable, note: receipt.note || undefined },
        { type: 'ACCEPTED_EXCESS', quantity: receipt.excess, note: receipt.note || undefined },
      ]
      return { purchaseLineId: line.id, dispositions: candidates.filter((item) => item.quantity && Number(item.quantity) > 0) }
    }).filter((line) => line.dispositions.length)
    return lines.length ? { lines } : null
  }

  function updateReceipt(lineId: string, key: keyof ReceiptInputs, value: string) {
    setPreview(null)
    confirmationKey.current = null
    setReceipts((current) => ({ ...current, [lineId]: { ...(current[lineId] ?? emptyReceipt()), [key]: value } }))
  }

  async function reviewReceipt() {
    const command = draft()
    if (!selected || !command) return
    try { setPreview(await previewReceipt(selected.id, command)); confirmationKey.current = crypto.randomUUID(); setFeedback('Review the canonical stock delta before confirming.') }
    catch { setFeedback('Could not preview receipt. Check quantities and excess notes.') }
  }

  async function confirmReviewedReceipt() {
    const command = draft()
    if (!selected || !command || !preview || !confirmationKey.current) return
    const purchaseId = selected.id
    try {
      const result = await confirmReceipt(purchaseId, command, confirmationKey.current)
      setReceipts((current) => ({ ...current, ...Object.fromEntries(command.lines.map((line) => [line.purchaseLineId, emptyReceipt()])) }))
      setFeedback(result.replayed ? 'Receipt was already confirmed.' : 'Receipt confirmed and stock refreshed.')
      setPreview(null)
      confirmationKey.current = null
    } catch { setFeedback('Could not confirm receipt. No stock changes were applied.') }
    if (confirmationKey.current) return
    try {
      const [detail, purchaseData] = await Promise.all([getPurchase(purchaseId), listPurchases()])
      setSelected(detail)
      setPurchases(purchaseData)
    } catch { setFeedback('Receipt confirmed, but refreshed data could not be loaded.') }
  }

  if (status === 'loading') return <AdminLoadingState label="Loading purchases…" />
  if (status === 'error') return <section className="admin-page"><p role="alert">Could not load purchases. Retry when the service is available.</p><button className="btn btn-secondary" onClick={() => void refresh()}>Retry</button></section>

  return (
    <section className="admin-page" aria-label="Procurement administration">
      <div className="admin-page-header"><p className="admin-eyebrow">Procurement</p><h2>Purchases</h2><p>Manage suppliers, purchase evidence, and reviewed stock receipts.</p></div>
      {feedback ? <p role="status" className="admin-card-help">{feedback}</p> : null}
      <section className="admin-card" aria-label="Supplier controls">
        <h3>Suppliers</h3>
        <form onSubmit={submitSupplier} className="admin-form-actions"><label className="admin-field"><span>Supplier name</span><input value={supplierName} onChange={(event) => setSupplierName(event.target.value)} /></label><button className="btn btn-primary">Add supplier</button></form>
        <ul className="admin-list">{suppliers.map((supplier) => <li key={supplier.id} className="admin-list-item"><span>{supplier.name} · {supplier.active ? 'Active' : 'Inactive'}</span><button className="btn btn-secondary" onClick={() => void toggleSupplier(supplier)}>{supplier.active ? 'Deactivate' : 'Activate'}</button></li>)}</ul>
      </section>
      <section className="admin-card" aria-label="Mapping controls">
        <h3>Supplier item mappings</h3>
        <form onSubmit={submitMapping} className="admin-toolbar-grid">
          <label className="admin-field"><span>Supplier</span><select value={mapping.supplierId} onChange={(event) => setMapping((current) => ({ ...current, supplierId: event.target.value }))}>{suppliers.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="admin-field"><span>Supplier item code</span><input value={mapping.supplierItemCode} onChange={(event) => setMapping((current) => ({ ...current, supplierItemCode: event.target.value }))} /></label>
          <label className="admin-field"><span>Mapping description</span><input value={mapping.description} onChange={(event) => setMapping((current) => ({ ...current, description: event.target.value }))} /></label>
          <label className="admin-field"><span>Target type</span><select value={mapping.targetType} onChange={(event) => setMapping((current) => ({ ...current, targetType: event.target.value }))}><option value="VARIANT_UNIT">Variant units</option><option value="BULK_GRAM">Bulk grams</option></select></label>
          <label className="admin-field"><span>Target ID</span><input value={mapping.targetId} onChange={(event) => setMapping((current) => ({ ...current, targetId: event.target.value }))} /></label>
          <label className="admin-field"><span>Default conversion</span><input inputMode="decimal" value={mapping.conversion} onChange={(event) => setMapping((current) => ({ ...current, conversion: event.target.value }))} /></label>
          <button className="btn btn-primary">Add mapping</button>
        </form>
        <ul className="admin-list">{mappings.map((item) => <li key={item.id} className="admin-list-item"><span>{item.supplierItemCode} · {item.description} · {item.active ? 'Active' : 'Inactive'}</span><button className="btn btn-secondary" onClick={() => void toggleMapping(item)}>{item.active ? 'Deactivate' : 'Activate'}</button></li>)}</ul>
      </section>
      <section className="admin-card" aria-label="Purchase registration">
        <h3>Register purchase</h3>
        <form onSubmit={submitPurchase} className="admin-toolbar-grid">
          <label className="admin-field"><span>Purchase supplier</span><select value={purchase.supplierId} onChange={(event) => setPurchase((current) => ({ ...current, supplierId: event.target.value }))}>{suppliers.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label className="admin-field"><span>Document type</span><input value={purchase.documentType} onChange={(event) => setPurchase((current) => ({ ...current, documentType: event.target.value }))} /></label>
          <label className="admin-field"><span>Document number</span><input value={purchase.documentNumber} onChange={(event) => setPurchase((current) => ({ ...current, documentNumber: event.target.value }))} /></label>
          <label className="admin-field"><span>Supplier mapping</span><select value={purchase.mappingId} onChange={(event) => setPurchase((current) => ({ ...current, mappingId: event.target.value }))}>{mappings.filter((item) => item.active && (!purchase.supplierId || item.supplierId === purchase.supplierId)).map((item) => <option key={item.id} value={item.id}>{item.supplierItemCode} · {item.description}</option>)}</select></label>
          <label className="admin-field"><span>Ordered quantity</span><input inputMode="decimal" value={purchase.orderedQuantity} onChange={(event) => setPurchase((current) => ({ ...current, orderedQuantity: event.target.value }))} /></label>
          <label className="admin-field"><span>Adjusted conversion (optional)</span><input inputMode="decimal" value={purchase.conversion} onChange={(event) => setPurchase((current) => ({ ...current, conversion: event.target.value }))} /></label>
          <button className="btn btn-primary">Register purchase</button>
        </form>
      </section>
      {!purchases.length ? <AdminEmptyState title="No purchases" action={<button className="btn btn-secondary" onClick={() => void refresh()}>Refresh</button>} /> : (
        <ul className="admin-list" aria-label="Purchases">{purchases.map((purchase) => <li key={purchase.id} className="admin-list-item"><button className="admin-item-card" onClick={() => void selectPurchase(purchase.id)}><strong>{purchase.supplierName}</strong><span>{purchase.documentType} {purchase.documentNumber}</span><span>{purchase.status}</span>{purchase.progress ? <span>{purchase.progress}</span> : null}</button></li>)}</ul>
      )}
      {selected ? <section className="admin-card" aria-label="Purchase detail"><h3>{selected.supplierName} · {selected.documentNumber}</h3><p>Status: {selected.status}</p><ul>{selected.lines?.map((line) => <li key={line.id}>{line.supplierDescription}: {String(line.orderedQuantity)} × {String(line.conversion)}</li>)}</ul>
        {selected.status === 'PENDING' ? <><section aria-label="Pending purchase editor"><h4>Edit pending purchase</h4>{selected.lines?.map((line) => <div className="admin-toolbar-grid" key={line.id}><label className="admin-field"><span>Edit ordered quantity {line.supplierDescription}</span><input inputMode="decimal" value={editableLines[line.id]?.orderedQuantity ?? String(line.orderedQuantity)} onChange={(event) => setEditableLines((current) => ({ ...current, [line.id]: { ...(current[line.id] ?? { orderedQuantity: String(line.orderedQuantity), conversion: String(line.conversion) }), orderedQuantity: event.target.value } }))} /></label><label className="admin-field"><span>Edit conversion {line.supplierDescription}</span><input inputMode="decimal" value={editableLines[line.id]?.conversion ?? String(line.conversion)} onChange={(event) => setEditableLines((current) => ({ ...current, [line.id]: { ...(current[line.id] ?? { orderedQuantity: String(line.orderedQuantity), conversion: String(line.conversion) }), conversion: event.target.value } }))} /></label></div>)}<button className="btn btn-secondary" onClick={() => void savePendingPurchase()}>Save pending purchase</button></section><section aria-label="Receipt editor"><h4>Receive purchase lines</h4>{selected.lines?.map((line) => { const receipt = receipts[line.id] ?? emptyReceipt(); return <fieldset key={line.id}><legend>{line.supplierDescription}</legend><div className="admin-toolbar-grid">{Object.entries({ accepted: 'Accepted', temporaryMissing: 'Temporarily missing', rejected: 'Rejected final', notDeliverable: 'Not deliverable final', excess: 'Accepted excess' }).map(([key, label]) => <label className="admin-field" key={key}><span>{label} {line.supplierDescription}</span><input inputMode="decimal" value={receipt[key as keyof ReceiptInputs]} onChange={(event) => updateReceipt(line.id, key as keyof ReceiptInputs, event.target.value)} /></label>)}<label className="admin-field"><span>Reason / note {line.supplierDescription}</span><input value={receipt.note} onChange={(event) => updateReceipt(line.id, 'note', event.target.value)} /></label></div></fieldset>})}<div className="admin-form-actions"><button className="btn btn-secondary" onClick={() => void reviewReceipt()}>Preview stock delta</button><button className="btn btn-primary" disabled={!preview} onClick={() => void confirmReviewedReceipt()}>Confirm reviewed receipt</button></div>{preview ? <ul aria-label="Canonical stock delta">{preview.canonicalDeltas.map((delta) => <li key={`${delta.targetType}-${delta.targetId}`}>{delta.targetType} {delta.targetId}: {delta.delta > 0 ? '+' : ''}{delta.delta}</li>)}</ul> : null}</section></> : null}
      </section> : null}
    </section>
  )
}
