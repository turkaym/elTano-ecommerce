import { useEffect, useRef, useState } from 'react'
import {
  addPurchaseDraftLine,
  confirmPurchaseDraft,
  createManualPurchaseDraft,
  deletePurchaseDraftLine,
  downloadPurchaseDraftSource,
  downloadPurchaseDraftTemplate,
  getPurchaseDraft,
  importPurchaseWorkbook,
  matchPurchaseDraftLine,
  previewPurchaseDraft,
  updatePurchaseDraftLine,
  type PurchaseDraft,
  type PurchaseDraftConfirmation,
  type PurchaseDraftLineCommand,
  type PurchaseDraftPreview,
  type SupplierDto,
} from '../services/procurementService'
import { createOperationKey, procurementErrorMessage, saveBlob } from './procurementUi'

export function usePurchaseDraftWorkflow(suppliers: SupplierDto[], onConfirmed: () => Promise<void>) {
  const [supplierId, setSupplierId] = useState('')
  const [draft, setDraft] = useState<PurchaseDraft | null>(null)
  const [preview, setPreview] = useState<PurchaseDraftPreview | null>(null)
  const [confirmation, setConfirmation] = useState<PurchaseDraftConfirmation | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<string | null>(null)
  const [confirmationFailed, setConfirmationFailed] = useState(false)
  const uploadAttempt = useRef<{ fingerprint: string; key: string } | null>(null)
  const confirmationKey = useRef<string | null>(null)

  useEffect(() => {
    if (!supplierId) setSupplierId(suppliers.find((supplier) => supplier.active)?.id ?? '')
  }, [supplierId, suppliers])

  function replaceDraft(nextDraft: PurchaseDraft) {
    setDraft(nextDraft)
    setPreview(null)
    setConfirmation(null)
    setConfirmationFailed(false)
    confirmationKey.current = null
  }

  async function run<T>(action: string, operation: () => Promise<T>): Promise<T | null> {
    setPendingAction(action)
    setError(null)
    try {
      return await operation()
    } catch (caught) {
      setError(procurementErrorMessage(caught))
      return null
    } finally {
      setPendingAction(null)
    }
  }

  async function downloadTemplate() {
    const blob = await run('template', downloadPurchaseDraftTemplate)
    if (blob) saveBlob(blob, 'plantilla-compras.xlsx')
  }

  async function upload(file: File) {
    if (!supplierId) return setError('Seleccioná un proveedor antes de importar el archivo.')
    const fingerprint = `${supplierId}:${file.name}:${file.size}:${file.lastModified}`
    if (uploadAttempt.current?.fingerprint !== fingerprint) uploadAttempt.current = { fingerprint, key: createOperationKey() }
    const result = await run('upload', () => importPurchaseWorkbook(supplierId, file, uploadAttempt.current!.key))
    if (result) {
      replaceDraft(result)
      uploadAttempt.current = null
    }
  }

  async function createManual(purchaseDate: string, line: { productName: string; quantity: string; unit: string; unitPrice: string }) {
    if (!supplierId) return setError('Seleccioná un proveedor antes de crear el borrador.')
    const result = await run('manual', () => createManualPurchaseDraft({ supplierId, purchaseDate, lines: [line] }))
    if (result) replaceDraft(result)
  }

  async function openDraft(id: string) {
    const result = await run('open', () => getPurchaseDraft(id))
    if (result) {
      setSupplierId(result.supplierId)
      replaceDraft(result)
    }
  }

  async function mutate(action: string, operation: () => Promise<PurchaseDraft>) {
    const result = await run(action, operation)
    if (result) replaceDraft(result)
  }

  async function addLine(line: Omit<PurchaseDraftLineCommand, 'version'>) {
    if (draft) await mutate('line', () => addPurchaseDraftLine(draft.id, { ...line, version: draft.version }))
  }

  async function updateLine(lineId: string, line: Omit<PurchaseDraftLineCommand, 'version'>) {
    if (draft) await mutate('line', () => updatePurchaseDraftLine(draft.id, lineId, { ...line, version: draft.version }))
  }

  async function deleteLine(lineId: string) {
    if (draft) await mutate('line', () => deletePurchaseDraftLine(draft.id, lineId, draft.version))
  }

  async function matchLine(lineId: string, targetId: string, remember: boolean) {
    if (draft) await mutate('match', () => matchPurchaseDraftLine(draft.id, lineId, { version: draft.version, targetId, remember }))
  }

  async function generatePreview() {
    if (!draft) return
    const result = await run('preview', () => previewPurchaseDraft(draft.id, draft.version))
    if (result) {
      setPreview(result)
      confirmationKey.current = createOperationKey()
      setConfirmationFailed(false)
    }
  }

  async function confirm() {
    if (!draft || !preview?.previewHash) return
    confirmationKey.current ??= createOperationKey()
    const result = await run('confirm', () => confirmPurchaseDraft(draft.id, { version: preview.version, previewHash: preview.previewHash! }, confirmationKey.current!))
    if (!result) return setConfirmationFailed(true)
    setDraft({ ...draft, status: 'CONFIRMED', confirmedPurchaseId: result.purchaseId, confirmedReceiptId: result.receiptId })
    setPreview(null)
    setConfirmation(result)
    setConfirmationFailed(false)
    try {
      await onConfirmed()
    } catch {
      setError('La compra se confirmó, pero no pudimos actualizar el historial. Recargá la página para verlo.')
    }
  }

  async function downloadSource() {
    if (!draft) return
    const result = await run('source', () => downloadPurchaseDraftSource(draft.id))
    if (result) saveBlob(result.blob, result.filename)
  }

  return {
    supplierId, setSupplierId, draft, preview, confirmation, error, pendingAction, confirmationFailed,
    clearError: () => setError(null), downloadTemplate, upload, createManual, openDraft, addLine, updateLine,
    deleteLine, matchLine, generatePreview, confirm, downloadSource,
  }
}
