import { useEffect, useState } from 'react'
import { createSupplier, listMappings, listPurchaseDrafts, listPurchases, listSuppliers, repairMapping, type MappingDto, type PurchaseDraft, type PurchaseDto, type SupplierDto } from '../services/procurementService'
import { AdminLoadingState } from './AdminPageStates'
import { ProcurementSecondaryPanels } from './ProcurementSecondaryPanels'
import { PurchaseDraftIntake } from './PurchaseDraftIntake'
import { PurchaseDraftReview } from './PurchaseDraftReview'
import { procurementErrorMessage } from './procurementUi'
import { usePurchaseDraftWorkflow } from './usePurchaseDraftWorkflow'

export function AdminProcurementPage() {
  const [suppliers, setSuppliers] = useState<SupplierDto[]>([])
  const [purchases, setPurchases] = useState<PurchaseDto[]>([])
  const [drafts, setDrafts] = useState<PurchaseDraft[]>([])
  const [mappings, setMappings] = useState<MappingDto[]>([])
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [loadError, setLoadError] = useState('')
  const [secondaryError, setSecondaryError] = useState('')
  const [reloadVersion, setReloadVersion] = useState(0)

  async function refreshHistory() {
    const [nextPurchases, nextDrafts] = await Promise.all([listPurchases(), listPurchaseDrafts()])
    setPurchases(nextPurchases)
    setDrafts(nextDrafts)
  }

  const workflow = usePurchaseDraftWorkflow(suppliers, refreshHistory)

  useEffect(() => {
    let active = true
    void Promise.all([listSuppliers(), listPurchases(), listPurchaseDrafts(), listMappings()])
      .then(([nextSuppliers, nextPurchases, nextDrafts, nextMappings]) => {
        if (!active) return
        setSuppliers(nextSuppliers)
        setPurchases(nextPurchases)
        setDrafts(nextDrafts)
        setMappings(nextMappings)
        setStatus('ready')
      })
      .catch((error) => {
        if (!active) return
        setLoadError(procurementErrorMessage(error))
        setStatus('error')
      })
    return () => { active = false }
  }, [reloadVersion])

  async function addSupplier(name: string, taxIdentity: string) {
    try {
      setSecondaryError('')
      await createSupplier({ name, ...(taxIdentity ? { taxIdentity } : {}) })
      setSuppliers(await listSuppliers())
    } catch (error) {
      setSecondaryError(procurementErrorMessage(error))
    }
  }

  async function repair(mapping: MappingDto, targetId: string, active: boolean) {
    try {
      setSecondaryError('')
      await repairMapping(mapping.id, { targetType: mapping.targetType, productId: mapping.targetType === 'BULK_GRAM' ? targetId : null, variantId: mapping.targetType === 'VARIANT_UNIT' ? targetId : null, defaultConversion: mapping.defaultConversion, active })
      setMappings(await listMappings())
    } catch (error) {
      setSecondaryError(procurementErrorMessage(error))
    }
  }

  return <section className="admin-page procurement-page">
    <header className="admin-page-header procurement-page-header">
      <div><p className="admin-eyebrow">Compras e inventario</p><h1>Ingreso de mercadería</h1><p>Importá, revisá y confirmá las compras antes de actualizar el stock.</p></div>
      <span className="procurement-safety-note">Sin cambios de stock hasta confirmar</span>
    </header>

    {status === 'loading' ? <AdminLoadingState label="Cargando compras y proveedores…" /> : null}
    {status === 'error' ? <div className="admin-feedback admin-feedback-error" role="alert"><p>{loadError}</p><button type="button" className="btn btn-secondary" onClick={() => { setStatus('loading'); setReloadVersion((version) => version + 1) }}>Reintentar</button></div> : null}

    {status === 'ready' ? <>
      <PurchaseDraftIntake suppliers={suppliers} supplierId={workflow.supplierId} pendingAction={workflow.pendingAction} onSupplierChange={workflow.setSupplierId} onDownloadTemplate={() => void workflow.downloadTemplate()} onUpload={(file) => void workflow.upload(file)} onCreateManual={(date, line) => void workflow.createManual(date, line)} />
      {workflow.error ? <div className="admin-feedback admin-feedback-error" role="alert"><p>{workflow.error}</p><button type="button" className="btn btn-secondary" onClick={workflow.clearError}>Cerrar</button></div> : null}
      {secondaryError ? <div className="admin-feedback admin-feedback-error" role="alert"><p>{secondaryError}</p><button type="button" className="btn btn-secondary" onClick={() => setSecondaryError('')}>Cerrar</button></div> : null}
      {workflow.draft ? <PurchaseDraftReview draft={workflow.draft} preview={workflow.preview} confirmation={workflow.confirmation} pendingAction={workflow.pendingAction} confirmationFailed={workflow.confirmationFailed} onAddLine={(line) => void workflow.addLine(line)} onUpdateLine={(id, line) => void workflow.updateLine(id, line)} onDeleteLine={(id) => void workflow.deleteLine(id)} onMatchLine={(id, target, remember) => void workflow.matchLine(id, target, remember)} onPreview={() => void workflow.generatePreview()} onConfirm={() => void workflow.confirm()} onDownloadSource={() => void workflow.downloadSource()} /> : null}
      <ProcurementSecondaryPanels suppliers={suppliers} purchases={purchases} drafts={drafts} mappings={mappings} onCreateSupplier={addSupplier} onOpenDraft={(id) => void workflow.openDraft(id)} onRepairMapping={repair} />
    </> : null}
  </section>
}
