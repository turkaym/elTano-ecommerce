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
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

export function AdminCatalogJobsPage() {
  const [rows, setRows] = useState<AdminCatalogJobRowDto[]>([])
  const [report, setReport] = useState<AdminCatalogJobReportDto | null>(null)
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [csvPayload, setCsvPayload] = useState('')
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
      setProgress(enqueue.status)
      const terminal = await awaitAdminImportTerminalStatus(enqueue.id, {
        maxAttempts: 10,
        delayMs: 0,
        onProgress: (next) => setProgress(next.status),
      })
      setProgress(terminal.status)
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

  if (status === 'loading') return <AdminLoadingState label="Cargando jobs de catálogo…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar jobs de catálogo." />
  if (!report) {
    return (
      <AdminEmptyState
        title="Sin jobs"
        action={
          <div>
            <textarea aria-label="Contenido CSV" value={csvPayload} onChange={(e) => setCsvPayload(e.target.value)} />
            <button type="button" onClick={onUpload} disabled={write.isPending}>
              Subir CSV
            </button>
            <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />
          </div>
        }
      />
    )
  }

  return (
    <section aria-label="Diagnóstico de catalog jobs">
      <h2>Catalog Jobs</h2>
      <p>{report.summary}</p>
      <p>Filas fallidas: {report.failedRows}</p>
      <p>{adminGuardMessages.cancelUnsupported}</p>
      <button type="button" disabled>
        Cancelar job (no soportado)
      </button>
      <textarea aria-label="Contenido CSV" value={csvPayload} onChange={(e) => setCsvPayload(e.target.value)} />
      <button type="button" onClick={onUpload} disabled={write.isPending}>
        Subir CSV
      </button>
      {progress ? <p>Estado: {progress}</p> : null}
      <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />
      <ul aria-label="Errores por fila">
        {rows.map((row) => (
          <li key={`${row.rowNumber}-${row.outcome}`}>
            Fila {row.rowNumber}: {row.errorMessage ?? 'Sin errores'}
          </li>
        ))}
      </ul>
    </section>
  )
}
