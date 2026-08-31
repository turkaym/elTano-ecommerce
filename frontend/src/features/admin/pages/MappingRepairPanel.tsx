import { type FormEvent, useDeferredValue, useEffect, useState } from 'react'
import { listCatalogCandidates, type CatalogCandidate, type MappingDto } from '../services/procurementService'

export function MappingRepairPanel({ mappings, onRepair }: { mappings: MappingDto[]; onRepair: (mapping: MappingDto, targetId: string, active: boolean) => Promise<void> }) {
  return <details className="admin-card"><summary>Equivalencias de proveedor <span>{mappings.length}</span></summary><div className="procurement-secondary-body">
    <p className="admin-card-help">Las correcciones afectan solo importaciones futuras. No modifican compras, recepciones ni movimientos históricos.</p>
    {!mappings.length ? <p className="admin-card-help">Todavía no hay equivalencias guardadas.</p> : <ul className="admin-list">{mappings.map((mapping) => <MappingRepair key={mapping.id} mapping={mapping} onRepair={onRepair} />)}</ul>}
  </div></details>
}

function MappingRepair({ mapping, onRepair }: { mapping: MappingDto; onRepair: (mapping: MappingDto, targetId: string, active: boolean) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const currentTargetId = mapping.productId ?? mapping.variantId ?? ''
  const [targetId, setTargetId] = useState(currentTargetId)
  const [active, setActive] = useState(mapping.active)
  const [candidates, setCandidates] = useState<CatalogCandidate[]>([])

  useEffect(() => {
    let mounted = true
    if (deferredQuery.trim().length < 2) return () => { mounted = false }
    const unit = mapping.targetType === 'BULK_GRAM' ? 'KG' : 'UNIDAD'
    void listCatalogCandidates(deferredQuery.trim(), unit).then((values) => { if (mounted) setCandidates(values) }).catch(() => { if (mounted) setCandidates([]) })
    return () => { mounted = false }
  }, [deferredQuery, mapping.targetType])

  async function submit(event: FormEvent) {
    event.preventDefault()
    await onRepair(mapping, targetId, active)
    setEditing(false)
  }

  return <li className="admin-item-card"><div className="admin-item-main"><strong>{mapping.supplierItemCode} → {mapping.targetLabel ?? 'Destino no disponible'}</strong><span>{mapping.targetType === 'BULK_GRAM' ? 'Producto a granel' : 'Variante'} · {mapping.active ? 'Activa' : 'Inactiva'}</span></div>
    {!editing ? <button type="button" className="btn btn-secondary" onClick={() => setEditing(true)}>Corregir equivalencia</button> : <form className="procurement-match-editor" onSubmit={submit}>
      <label><span>Buscar nuevo destino</span><input aria-label={`Buscar nuevo destino para ${mapping.supplierItemCode}`} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar en catálogo" /></label>
      <label><span>Destino</span><select aria-label={`Destino para ${mapping.supplierItemCode}`} value={targetId} onChange={(event) => setTargetId(event.target.value)}><option value={currentTargetId}>{mapping.targetLabel ?? currentTargetId}</option>{candidates.filter((candidate) => candidate.targetType === mapping.targetType && candidate.value !== currentTargetId).map((candidate) => <option key={candidate.value} value={candidate.value}>{candidate.label}</option>)}</select></label>
      <label className="procurement-remember"><input aria-label={`Equivalencia activa para ${mapping.supplierItemCode}`} type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /> Activa para futuras importaciones</label>
      <button className="btn btn-primary" disabled={!targetId}>Guardar corrección</button><button type="button" className="btn btn-secondary" onClick={() => setEditing(false)}>Cancelar</button>
    </form>}
  </li>
}
