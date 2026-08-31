import { type FormEvent, useDeferredValue, useEffect, useState } from 'react'
import { listCatalogCandidates, type CatalogCandidate, type PurchaseDraft, type PurchaseDraftConfirmation, type PurchaseDraftLine, type PurchaseDraftPreview } from '../services/procurementService'

interface Props {
  draft: PurchaseDraft
  preview: PurchaseDraftPreview | null
  confirmation: PurchaseDraftConfirmation | null
  pendingAction: string | null
  confirmationFailed: boolean
  onAddLine: (line: { productName: string; quantity: string; unit: string }) => void
  onUpdateLine: (lineId: string, line: { productName: string; quantity: string; unit: string }) => void
  onDeleteLine: (lineId: string) => void
  onMatchLine: (lineId: string, targetId: string, remember: boolean) => void
  onPreview: () => void
  onConfirm: () => void
  onDownloadSource: () => void
}

const statusLabels = { MATCHED: 'Vinculado', UNRESOLVED: 'Sin resolver', INVALID: 'Inválido', DUPLICATE: 'Duplicado' } as const

export function PurchaseDraftReview(props: Props) {
  if (props.draft.status === 'CONFIRMED') return <ConfirmedDraftEvidence draft={props.draft} confirmation={props.confirmation} onDownloadSource={props.onDownloadSource} />

  const counts = props.draft.lines.reduce((result, line) => {
    const status = displayStatus(line)
    result[status] += 1
    return result
  }, { MATCHED: 0, UNRESOLVED: 0, INVALID: 0, DUPLICATE: 0 })
  const hasBlockingRows = counts.DUPLICATE > 0 || counts.INVALID > 0

  return (
    <section className="admin-card procurement-review" aria-labelledby="draft-review-title">
      <header className="admin-card-header procurement-review-header">
        <div>
          <p className="admin-eyebrow">Revisión previa</p>
          <h2 id="draft-review-title">{props.draft.supplierName}</h2>
          <p>{props.draft.originalFilename ?? 'Carga manual'} · {props.draft.purchaseDate}</p>
        </div>
        {props.draft.sourceType === 'XLSX' ? <button type="button" className="btn btn-secondary" onClick={props.onDownloadSource}>Descargar archivo original</button> : null}
      </header>

      <div className="procurement-status-grid" aria-label="Resumen de filas">
        <StatusCount value={counts.MATCHED} label="vinculadas" tone="success" />
        <StatusCount value={counts.UNRESOLVED} label="sin resolver" tone="warning" />
        <StatusCount value={counts.INVALID} label="inválidas" tone="danger" />
        <StatusCount value={counts.DUPLICATE} label="duplicadas" tone="danger" />
      </div>

      {hasBlockingRows ? <p className="admin-feedback admin-feedback-error" role="alert">Corregí las filas inválidas o duplicadas antes de generar la vista previa.</p> : null}

      <div className="procurement-table-wrap">
        <table className="procurement-table">
          <caption>Productos detectados en la compra</caption>
          <thead><tr><th>Fila</th><th>Producto</th><th>Cantidad</th><th>Estado</th><th>Catálogo</th><th>Acciones</th></tr></thead>
          <tbody>{props.draft.lines.map((line) => <DraftLineRow key={line.id} line={line} pending={props.pendingAction === 'line' || props.pendingAction === 'match'} onUpdate={props.onUpdateLine} onDelete={props.onDeleteLine} onMatch={props.onMatchLine} />)}</tbody>
        </table>
      </div>

      <AddLineForm onAdd={props.onAddLine} disabled={props.pendingAction === 'line'} />

      <div className="procurement-preview-actions">
        <div><strong>El stock todavía no cambió</strong><span>Primero generá y revisá el impacto final.</span></div>
        <button type="button" className="btn btn-primary" disabled={hasBlockingRows || !props.draft.lines.length || props.pendingAction === 'preview'} onClick={props.onPreview}>Generar vista previa</button>
      </div>

      {props.preview ? <PreviewPanel draft={props.draft} preview={props.preview} pending={props.pendingAction === 'confirm'} retry={props.confirmationFailed} onConfirm={props.onConfirm} /> : null}
      {props.confirmation ? <ConfirmationPanel draft={props.draft} confirmation={props.confirmation} /> : null}
    </section>
  )
}

function StatusCount({ value, label, tone }: { value: number; label: string; tone: string }) {
  return <div className={`procurement-status-card ${tone}`}><strong>{value}</strong><span>{label}</span></div>
}

function DraftLineRow({ line, pending, onUpdate, onDelete, onMatch }: { line: PurchaseDraftLine; pending: boolean; onUpdate: Props['onUpdateLine']; onDelete: Props['onDeleteLine']; onMatch: Props['onMatchLine'] }) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(line.productName)
  const [quantity, setQuantity] = useState(String(line.quantity ?? ''))
  const [unit, setUnit] = useState<'UNIDAD' | 'KG'>(line.unit ?? 'UNIDAD')

  if (editing) return (
    <tr className="procurement-edit-row">
      <td>{line.rowNumber}</td>
      <td><label><span className="sr-only">Producto de fila {line.rowNumber}</span><input value={name} onChange={(event) => setName(event.target.value)} /></label></td>
      <td><label><span className="sr-only">Cantidad de fila {line.rowNumber}</span><input type="number" step="any" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label></td>
      <td><label><span className="sr-only">Unidad de fila {line.rowNumber}</span><select value={unit ?? 'UNIDAD'} onChange={(event) => setUnit(event.target.value as 'UNIDAD' | 'KG')}><option value="UNIDAD">Unidad</option><option value="KG">Kilogramo</option></select></label></td>
      <td colSpan={2}><div className="procurement-row-actions"><button className="btn btn-primary" type="button" disabled={pending} onClick={() => { onUpdate(line.id, { productName: name, quantity, unit }); setEditing(false) }}>Guardar</button><button className="btn btn-secondary" type="button" onClick={() => setEditing(false)}>Cancelar</button></div></td>
    </tr>
  )

  return (
    <tr>
      <td data-label="Fila">{line.rowNumber}</td>
      <td data-label="Producto"><strong>{line.productName}</strong>{line.errors.length ? <small>{line.errors.join(' ')}</small> : null}</td>
      <td data-label="Cantidad">{line.quantity} {humanUnit(line.unit ?? '')}</td>
      <td data-label="Estado"><span className={`procurement-line-status ${displayStatus(line).toLowerCase()}`}>{statusLabels[displayStatus(line)]}</span></td>
      <td data-label="Catálogo">{line.matchStatus === 'MATCHED' ? <TargetLink line={line} /> : line.matchStatus === 'UNRESOLVED' ? <MatchEditor line={line} pending={pending} onMatch={onMatch} /> : 'Corregí la fila para vincularla'}</td>
      <td data-label="Acciones"><div className="procurement-row-actions"><button className="btn btn-secondary" type="button" onClick={() => setEditing(true)}>Editar</button><button className="btn btn-secondary" type="button" disabled={pending} onClick={() => onDelete(line.id)}>Eliminar</button></div></td>
    </tr>
  )
}

function MatchEditor({ line, pending, onMatch }: { line: PurchaseDraftLine; pending: boolean; onMatch: Props['onMatchLine'] }) {
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const [candidates, setCandidates] = useState<CatalogCandidate[]>([])
  const [selected, setSelected] = useState('')
  const [remember, setRemember] = useState(false)

  useEffect(() => {
    let active = true
    if (deferredQuery.trim().length < 2) {
      return () => { active = false }
    }
    void listCatalogCandidates(deferredQuery.trim(), line.unit ?? 'UNIDAD').then((result) => { if (active) setCandidates(result) }).catch(() => { if (active) setCandidates([]) })
    return () => { active = false }
  }, [deferredQuery, line.unit])

  const visibleCandidates = deferredQuery.trim().length >= 2 ? candidates : []

  return <div className="procurement-match-editor">
    <label><span className="sr-only">Buscar producto para {line.productName}</span><input aria-label={`Buscar producto para ${line.productName}`} placeholder="Buscar en catálogo" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
    <label><span className="sr-only">Resultado para {line.productName}</span><select aria-label={`Resultado para ${line.productName}`} value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">Seleccionar coincidencia</option>{visibleCandidates.map((candidate) => <option key={candidate.value} value={candidate.value}>{candidate.label}</option>)}</select></label>
    <label className="procurement-remember"><input aria-label={`Recordar equivalencia para ${line.productName}`} type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} /> Recordar solo para futuras importaciones</label>
    <button type="button" className="btn btn-secondary" aria-label={`Vincular ${line.productName}`} disabled={!selected || pending} onClick={() => onMatch(line.id, selected, remember)}>Vincular</button>
  </div>
}

function AddLineForm({ onAdd, disabled }: { onAdd: Props['onAddLine']; disabled: boolean }) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [unit, setUnit] = useState('UNIDAD')
  function submit(event: FormEvent) { event.preventDefault(); onAdd({ productName: name.trim(), quantity, unit }); setName(''); setOpen(false) }
  if (!open) return <button type="button" className="btn btn-secondary procurement-add-line" onClick={() => setOpen(true)}>Agregar producto</button>
  return <form className="procurement-add-form" onSubmit={submit}><label className="admin-field"><span>Producto nuevo</span><input value={name} onChange={(event) => setName(event.target.value)} required /></label><label className="admin-field"><span>Cantidad nueva</span><input type="number" step="any" min="0.001" value={quantity} onChange={(event) => setQuantity(event.target.value)} required /></label><label className="admin-field"><span>Unidad nueva</span><select value={unit} onChange={(event) => setUnit(event.target.value)}><option value="UNIDAD">Unidad</option><option value="KG">Kilogramo</option></select></label><button className="btn btn-primary" disabled={disabled}>Agregar</button><button type="button" className="btn btn-secondary" onClick={() => setOpen(false)}>Cancelar</button></form>
}

function PreviewPanel({ draft, preview, pending, retry, onConfirm }: { draft: PurchaseDraft; preview: PurchaseDraftPreview; pending: boolean; retry: boolean; onConfirm: () => void }) {
  return <section className="procurement-preview" aria-labelledby="preview-title"><header><p className="admin-eyebrow">Impacto en inventario</p><h3 id="preview-title">Vista previa canónica</h3></header>{preview.errors.length ? <ul className="admin-feedback admin-feedback-error" role="alert">{preview.errors.map((error) => <li key={`${error.rowNumber}-${error.code}-${error.message}`}>{error.rowNumber ? `Fila ${error.rowNumber}: ` : ''}{error.message}</li>)}</ul> : null}<ul className="procurement-delta-list">{preview.canonicalDeltas.map((delta) => { const line = draft.lines.find((item) => item.id === delta.lineId); return <li key={delta.lineId}><span><strong>{line?.productName}</strong> · {line?.quantity} {humanUnit(line?.unit ?? '')} → {line?.targetLabel} <small>{targetIdentity(line)}</small></span><strong>+{delta.delta}</strong></li> })}</ul><button type="button" className="btn btn-primary" disabled={!preview.ready || pending} onClick={onConfirm}>{pending ? 'Confirmando…' : retry ? 'Reintentar confirmación' : 'Confirmar compra'}</button></section>
}

function ConfirmationPanel({ draft, confirmation }: { draft: PurchaseDraft; confirmation: PurchaseDraftConfirmation }) {
  return <section className="procurement-success" role="status"><p className="admin-eyebrow">Inventario actualizado</p><h3>Compra confirmada</h3><p>Comprobante de recepción registrado</p><ul>{confirmation.canonicalDeltas.map((delta) => <li key={delta.lineId}>{lineName(draft, delta.lineId)}: +{delta.delta}</li>)}</ul>{confirmation.replayed ? <small>La confirmación ya había sido procesada; no se duplicó el stock.</small> : null}</section>
}

function ConfirmedDraftEvidence({ draft, confirmation, onDownloadSource }: { draft: PurchaseDraft; confirmation: PurchaseDraftConfirmation | null; onDownloadSource: () => void }) {
  return <section className="admin-card procurement-review" aria-labelledby="draft-review-title">
    <header className="admin-card-header procurement-review-header"><div><p className="admin-eyebrow">Evidencia confirmada</p><h2 id="draft-review-title">{draft.supplierName}</h2><p>{draft.originalFilename ?? 'Carga manual'} · {draft.purchaseDate}</p></div>{draft.sourceType === 'XLSX' ? <button type="button" className="btn btn-secondary" onClick={onDownloadSource}>Descargar archivo original</button> : null}</header>
    <div className="procurement-success" role="status"><h3>Compra confirmada</h3><p>Comprobante de recepción registrado</p><p>Compra: <code>{draft.confirmedPurchaseId}</code></p><p>Recepción: <code>{draft.confirmedReceiptId}</code></p>{confirmation?.replayed ? <small>La confirmación ya había sido procesada; no se duplicó el stock.</small> : null}</div>
    <div className="procurement-table-wrap"><table className="procurement-table"><caption>Detalle inmutable de la importación</caption><thead><tr><th>Fila</th><th>Origen</th><th>Cantidad original</th><th>Destino y referencia</th><th>Conversión</th><th>Variación canónica</th></tr></thead><tbody>{draft.lines.map((line) => <tr key={line.id}><td>{line.rowNumber}</td><td><strong>{line.productName}</strong></td><td>{line.sourceQuantity} {humanUnit(line.unit ?? '')}</td><td><TargetLink line={line} /></td><td>× {line.conversion}</td><td><strong>+{line.canonicalDelta}</strong></td></tr>)}</tbody></table></div>
  </section>
}

function TargetLink({ line }: { line: PurchaseDraftLine }) {
  return <span>{line.productName} → <strong>{line.targetLabel ?? 'Destino no disponible'}</strong> <small>{targetIdentity(line)}</small>{!line.targetLabelPersisted ? <small> Etiqueta reconstruida del catálogo actual; no es un nombre histórico exacto.</small> : null}</span>
}

function targetIdentity(line?: PurchaseDraftLine) {
  if (!line?.targetType) return ''
  return `(${line.targetType === 'BULK_GRAM' ? 'producto a granel' : 'variante'}: ${line.productId ?? line.variantId})`
}

function humanUnit(unit: string) {
  return ({ UNIDAD: 'u.', KG: 'kg' } as Record<string, string>)[unit] ?? unit.toLowerCase()
}

function displayStatus(line: PurchaseDraftLine): keyof typeof statusLabels {
  return line.errors.some((error) => error.toLowerCase().includes('duplicada')) ? 'DUPLICATE' : line.matchStatus
}

function lineName(draft: PurchaseDraft, lineId: string) {
  return draft.lines.find((line) => line.id === lineId)?.productName ?? 'Producto vinculado'
}
