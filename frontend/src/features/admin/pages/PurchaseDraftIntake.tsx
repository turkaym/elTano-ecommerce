import { type DragEvent, type FormEvent, useState } from 'react'
import type { SupplierDto } from '../services/procurementService'

interface Props {
  suppliers: SupplierDto[]
  supplierId: string
  pendingAction: string | null
  onSupplierChange: (id: string) => void
  onDownloadTemplate: () => void
  onUpload: (file: File) => void
  onCreateManual: (date: string, line: { productName: string; quantity: string; unit: string }) => void
}

export function PurchaseDraftIntake(props: Props) {
  const [tab, setTab] = useState<'excel' | 'manual'>('excel')
  const [file, setFile] = useState<File | null>(null)
  const [dragging, setDragging] = useState(false)
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [productName, setProductName] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [unit, setUnit] = useState('UNIDAD')

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault()
    setDragging(false)
    const dropped = event.dataTransfer.files[0]
    if (dropped) setFile(dropped)
  }

  function submitManual(event: FormEvent) {
    event.preventDefault()
    props.onCreateManual(date, { productName: productName.trim(), quantity, unit })
  }

  return (
    <section className="admin-card procurement-intake" aria-labelledby="purchase-intake-title">
      <header className="admin-card-header">
        <p className="admin-eyebrow">Nuevo ingreso</p>
        <h2 id="purchase-intake-title">Registrar compra</h2>
        <p>Elegí el proveedor y cargá la planilla. Vas a poder revisar todo antes de sumar stock.</p>
      </header>

      <label className="admin-field procurement-supplier-field">
        <span>Proveedor</span>
        <select value={props.supplierId} onChange={(event) => props.onSupplierChange(event.target.value)} required>
          <option value="">Seleccionar proveedor</option>
          {props.suppliers.filter((supplier) => supplier.active).map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}
        </select>
      </label>

      <div className="procurement-tabs" role="tablist" aria-label="Forma de carga">
        <button type="button" role="tab" aria-selected={tab === 'excel'} className={tab === 'excel' ? 'active' : ''} onClick={() => setTab('excel')}>Importar Excel</button>
        <button type="button" role="tab" aria-selected={tab === 'manual'} className={tab === 'manual' ? 'active' : ''} onClick={() => setTab('manual')}>Carga manual</button>
      </div>

      {tab === 'excel' ? (
        <div role="tabpanel" className="procurement-upload-panel">
          <div className="procurement-template-callout">
            <div><strong>Usá la plantilla oficial</strong><span>Incluye las columnas y unidades aceptadas.</span></div>
            <button type="button" className="btn btn-secondary" onClick={props.onDownloadTemplate} disabled={props.pendingAction === 'template'}>Descargar plantilla oficial</button>
          </div>
          <label className={`procurement-drop-zone${dragging ? ' is-dragging' : ''}`} onDragOver={(event) => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={handleDrop}>
            <span className="procurement-file-mark" aria-hidden="true">XLSX</span>
            <strong>{file ? file.name : 'Arrastrá tu archivo Excel acá'}</strong>
            <span>{file ? 'Archivo listo para importar' : 'o hacé clic para elegirlo'}</span>
            <input aria-label="Archivo Excel" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </label>
          <button type="button" className="btn btn-primary procurement-primary-action" disabled={!file || !props.supplierId || props.pendingAction === 'upload'} onClick={() => file && props.onUpload(file)}>
            {props.pendingAction === 'upload' ? 'Importando…' : 'Importar y revisar'}
          </button>
        </div>
      ) : (
        <form role="tabpanel" className="admin-form procurement-manual-form" onSubmit={submitManual}>
          <div className="admin-form-grid">
            <label className="admin-field"><span>Fecha de compra</span><input type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></label>
            <label className="admin-field"><span>Producto</span><input value={productName} onChange={(event) => setProductName(event.target.value)} required /></label>
            <label className="admin-field"><span>Cantidad</span><input type="number" min="0.001" step="any" value={quantity} onChange={(event) => setQuantity(event.target.value)} required /></label>
            <label className="admin-field"><span>Unidad</span><select value={unit} onChange={(event) => setUnit(event.target.value)}><option value="UNIDAD">Unidad</option><option value="KG">Kilogramo</option></select></label>
          </div>
          <button className="btn btn-primary" disabled={!props.supplierId || props.pendingAction === 'manual'}>Crear borrador</button>
        </form>
      )}
    </section>
  )
}
