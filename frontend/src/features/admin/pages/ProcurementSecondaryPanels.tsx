import { type FormEvent, useState } from 'react'
import type { MappingDto, PurchaseDraft, PurchaseDto, SupplierDto } from '../services/procurementService'
import { MappingRepairPanel } from './MappingRepairPanel'

interface Props {
  suppliers: SupplierDto[]
  purchases: PurchaseDto[]
  drafts: PurchaseDraft[]
  mappings: MappingDto[]
  onCreateSupplier: (name: string, taxIdentity: string) => Promise<void>
  onOpenDraft: (id: string) => void
  onRepairMapping: (mapping: MappingDto, targetId: string, active: boolean) => Promise<void>
}

export function ProcurementSecondaryPanels({ suppliers, purchases, drafts, mappings, onCreateSupplier, onOpenDraft, onRepairMapping }: Props) {
  const [name, setName] = useState('')
  const [taxIdentity, setTaxIdentity] = useState('')

  async function submitSupplier(event: FormEvent) {
    event.preventDefault()
    await onCreateSupplier(name.trim(), taxIdentity.trim())
    setName('')
    setTaxIdentity('')
  }

  return <section className="procurement-secondary" aria-label="Gestión complementaria de compras">
    <details className="admin-card">
      <summary>Borradores recientes <span>{drafts.filter((draft) => draft.status === 'DRAFT').length}</span></summary>
      <div className="procurement-secondary-body">
        {!drafts.length ? <p className="admin-card-help">Todavía no hay borradores guardados.</p> : <ul className="admin-list">{drafts.map((draft) => <li className="admin-item-card" key={draft.id}><div className="admin-item-main"><strong>{draft.supplierName}</strong><span>{draft.originalFilename ?? 'Carga manual'} · {draft.purchaseDate}</span><span className={`admin-badge ${draft.status === 'CONFIRMED' ? 'admin-badge-success' : 'admin-badge-muted'}`}>{draft.status === 'CONFIRMED' ? 'Confirmado' : 'Pendiente'}</span></div><button type="button" className="btn btn-secondary" onClick={() => onOpenDraft(draft.id)}>Abrir revisión</button></li>)}</ul>}
      </div>
    </details>

    <MappingRepairPanel mappings={mappings} onRepair={onRepairMapping} />

    <details className="admin-card">
      <summary>Proveedores <span>{suppliers.length}</span></summary>
      <div className="procurement-secondary-body">
        <form className="procurement-supplier-form" onSubmit={submitSupplier}>
          <label className="admin-field"><span>Nombre del proveedor</span><input value={name} onChange={(event) => setName(event.target.value)} required /></label>
          <label className="admin-field"><span>Identificación fiscal (opcional)</span><input value={taxIdentity} onChange={(event) => setTaxIdentity(event.target.value)} /></label>
          <button className="btn btn-secondary">Agregar proveedor</button>
        </form>
        <ul className="procurement-compact-list">{suppliers.map((supplier) => <li key={supplier.id}><strong>{supplier.name}</strong><span>{supplier.active ? 'Activo' : 'Inactivo'}</span></li>)}</ul>
      </div>
    </details>

    <details className="admin-card">
      <summary>Historial de compras <span>{purchases.length}</span></summary>
      <div className="procurement-secondary-body">
        {!purchases.length ? <p className="admin-card-help">Todavía no hay compras registradas.</p> : <ul className="procurement-compact-list">{purchases.map((purchase) => <li key={purchase.id}><div><strong>{purchase.supplierName}</strong><span>{purchase.documentType} {purchase.documentNumber}</span></div><span className={`admin-badge ${purchase.status === 'RECEIVED' ? 'admin-badge-success' : 'admin-badge-muted'}`}>{purchase.status === 'RECEIVED' ? 'Recibida' : purchase.status === 'CANCELLED' ? 'Cancelada' : 'Pendiente'}</span></li>)}</ul>}
      </div>
    </details>
  </section>
}
