import { useEffect, useState } from 'react'
import { adminGuardMessages } from '../auth/adminAccess'
import {
  awaitAdminImportTerminalStatus,
  createAdminImportJob,
  getAdminCatalogJobReport,
  getAdminCatalogJobRows,
  listAdminCatalogJobs,
  mapAdminWriteError,
  type AdminCatalogJobReportDto,
  type AdminCatalogJobRowDto,
  uploadAlegraCatalogImport,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

export function AdminCatalogJobsPage() {
  const [rows, setRows] = useState<AdminCatalogJobRowDto[]>([])
  const [report, setReport] = useState<AdminCatalogJobReportDto | null>(null)
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [csvPayload, setCsvPayload] = useState('')
  const [alegraFile, setAlegraFile] = useState<File | null>(null)
  const [progress, setProgress] = useState<string>('')
  const write = useAdminWriteState()

  useEffect(() => {
    let active = true
    void listAdminCatalogJobs()
      .then(async (jobs) => {
        if (!active) return
        if (!jobs.length) {
          setStatus('ready')
          return
        }
        const detail = jobs[0]
        const [jobRows, jobReport] = await Promise.all([
          getAdminCatalogJobRows(detail.id),
          getAdminCatalogJobReport(detail.id),
        ])
        if (!active) return
        setRows(jobRows)
        setReport(jobReport)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('error')
      })

    return () => {
      active = false
    }
  }, [])

  async function refreshLatestJob() {
    const jobs = await listAdminCatalogJobs()
    if (!jobs.length) return
    const detail = jobs[0]
    const [jobRows, jobReport] = await Promise.all([
      getAdminCatalogJobRows(detail.id),
      getAdminCatalogJobReport(detail.id),
    ])
    setRows(jobRows)
    setReport(jobReport)
  }

  async function onUpload() {
    if (write.isPending) return
    if (!csvPayload.trim().toLowerCase().includes(',')) {
      write.fail({ message: 'Archivo CSV inválido.' })
      return
    }

    write.start()
    setProgress('QUEUED')

    try {
      const enqueue = await createAdminImportJob(csvPayload)
      const terminal = await pollImportJob(enqueue.id, enqueue.status)
      await refreshLatestJob()
      if (terminal.status === 'FAILED') {
        write.fail({ message: terminal.lastError ?? 'El job finalizó con error.' })
      } else {
        write.succeed('Importación completada.')
      }
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  async function onAlegraUpload() {
    if (write.isPending) return
    if (!alegraFile || !isXlsxFile(alegraFile)) {
      write.fail({ message: 'Seleccioná un archivo .xlsx de Alegra.' })
      return
    }

    write.start()
    setProgress('QUEUED')

    try {
      const enqueue = await uploadAlegraCatalogImport(alegraFile)
      const terminal = await pollImportJob(enqueue.id, enqueue.status)
      await refreshLatestJob()
      if (terminal.status === 'FAILED') {
        write.fail({ message: terminal.lastError ?? 'La importación Alegra finalizó con error.' })
      } else {
        write.succeed('Importación Alegra completada.')
      }
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  async function pollImportJob(jobId: string, initialStatus: string) {
    setProgress(initialStatus)
    const terminal = await awaitAdminImportTerminalStatus(jobId, {
      maxAttempts: 10,
      delayMs: 0,
      onProgress: (next) => setProgress(next.status),
    })
    setProgress(terminal.status)
    return terminal
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando jobs de catálogo…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar jobs de catálogo." />

  return (
    <section className="admin-page" aria-label="Diagnóstico de catalog jobs">
      <div className="admin-page-header">
        <p className="admin-eyebrow">Operaciones</p>
        <h2>Catalog Jobs</h2>
        <p>Subí Excel de Alegra o CSV de catálogo y revisá el diagnóstico del último proceso importado.</p>
      </div>

      <section className="admin-card" aria-labelledby="alegra-upload-title">
        <div className="admin-card-header">
          <h3 id="alegra-upload-title">Importar Excel de Alegra</h3>
          <p>Subí el exportable .xlsx de productos de venta. La importación crea catálogo, precios y presentaciones; el stock y las imágenes se cargan manualmente después.</p>
        </div>
        <form
          className="admin-form"
          onSubmit={(event) => {
            event.preventDefault()
            void onAlegraUpload()
          }}
        >
          <label className="admin-field admin-field-wide">
            <span>Archivo Alegra .xlsx</span>
            <input
              aria-label="Archivo Alegra .xlsx"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              type="file"
              onChange={(event) => setAlegraFile(event.target.files?.[0] ?? null)}
            />
            <small>Usá el exportable de Alegra “Productos de venta”; no importa stock ni imágenes reales.</small>
          </label>
          {alegraFile ? <p className="admin-card-help">Seleccionado: {alegraFile.name}</p> : null}
          <div className="admin-form-actions">
            <button className="btn btn-primary" type="submit" disabled={write.isPending || !alegraFile}>
              Subir Excel Alegra
            </button>
          </div>
        </form>
      </section>

      <section className="admin-card" aria-labelledby="catalog-upload-title">
        <div className="admin-card-header">
          <h3 id="catalog-upload-title">Importar catálogo</h3>
          <p>{report ? 'Pegá el contenido CSV para encolar un nuevo job de importación.' : 'Importá tu primer CSV para generar un diagnóstico de catálogo.'}</p>
        </div>
        {!report ? (
          <div className="admin-catalog-empty-callout" aria-label="Estado inicial catalog jobs">
            <strong>Sin jobs todavía</strong>
            <span>Cuando subas un CSV, vas a ver el resumen del proceso y los errores por fila en esta pantalla.</span>
          </div>
        ) : null}
        <form
          className="admin-form"
          onSubmit={(event) => {
            event.preventDefault()
            void onUpload()
          }}
        >
          <label className="admin-field admin-field-wide">
            <span>Contenido CSV</span>
            <textarea
              aria-label="Contenido CSV"
              value={csvPayload}
              onChange={(e) => setCsvPayload(e.target.value)}
              placeholder={'name,slug\nPera,pera'}
            />
            <small>Formato esperado: encabezados en la primera fila y valores separados por coma.</small>
          </label>
          <div className="admin-form-actions">
            <button className="btn btn-secondary" type="button" disabled aria-describedby="catalog-cancel-help">
              Cancelar job (no soportado)
            </button>
            <button className="btn btn-primary" type="submit" disabled={write.isPending}>
              Subir CSV
            </button>
          </div>
        </form>
        <p id="catalog-cancel-help" className="admin-card-help">{adminGuardMessages.cancelUnsupported}</p>
      </section>

      {progress ? <p className="admin-feedback admin-feedback-pending">Estado: {progress}</p> : null}
      <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />

      {report ? (
        <>
          <section className="admin-dashboard-metrics" aria-label="Resumen del último job">
            <article className="admin-card admin-metric-card admin-catalog-summary-card">
              <span>Resumen</span>
              <strong>{report.summary}</strong>
              <small>Último reporte disponible</small>
            </article>
            <article className="admin-card admin-metric-card">
              <span>Filas fallidas</span>
              <strong>{report.failedRows}</strong>
              <small>Registros que requieren revisión</small>
            </article>
          </section>

          <section className="admin-card" aria-labelledby="catalog-row-errors-title">
            <div className="admin-card-header">
              <h3 id="catalog-row-errors-title">Errores por fila</h3>
              <p>Detalle de resultados por registro para corregir y volver a importar.</p>
            </div>
            {!rows.length ? (
              <AdminEmptyState title="Sin errores por fila" action={<p>No hay filas para revisar en el último reporte.</p>} />
            ) : (
              <ul className="admin-list admin-catalog-row-list" aria-label="Errores por fila">
                {rows.map((row) => (
                  <li className="admin-list-item" key={`${row.rowNumber}-${row.outcome}-${row.errorCode ?? 'ok'}`}>
                    <article className="admin-item-card" aria-label={`Fila ${row.rowNumber}`}>
                      <div className="admin-item-main">
                        <strong>Fila {row.rowNumber}</strong>
                        <span className={`admin-badge ${row.outcome === 'FAILED' ? 'admin-badge-danger' : 'admin-badge-success'}`}>
                          {row.outcome}
                        </span>
                        {row.errorCode ? <span>Código: {row.errorCode}</span> : null}
                        <span>{row.errorMessage ?? 'Sin errores'}</span>
                        {row.payload ? <code className="admin-catalog-row-payload">{row.payload}</code> : null}
                      </div>
                    </article>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      ) : null}
    </section>
  )
}

function isXlsxFile(file: File): boolean {
  return file.name.trim().toLowerCase().endsWith('.xlsx')
}
