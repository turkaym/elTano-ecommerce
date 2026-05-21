import { type FormEvent, useEffect, useState } from 'react'
import {
  getAdminOrderDetail,
  listAdminOrders,
  mapAdminWriteError,
  type AdminOrderDetailResponse,
  type AdminOrderListParams,
  type AdminOrderListResponse,
  type AdminOrderSummary,
  updateAdminOrderPaymentStatus,
  updateAdminOrderStatus,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

const DEFAULT_PAGE_SIZE = 20
const ORDER_STATUSES = ['', 'DRAFT', 'PAYMENT_PENDING', 'PAID', 'PREPARING', 'READY', 'DELIVERED', 'FAILED', 'CANCELLED', 'EXPIRED']
const TERMINAL_STATUSES = new Set(['DELIVERED', 'FAILED', 'CANCELLED', 'EXPIRED'])

interface OrderStatusAction {
  targetStatus: string
  label: string
  variant: 'primary' | 'secondary'
}

export function AdminOrdersPage() {
  const [data, setData] = useState<AdminOrderListResponse | null>(null)
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [filters, setFilters] = useState({ status: '', search: '', from: '', to: '' })
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)
  const [detail, setDetail] = useState<AdminOrderDetailResponse | null>(null)
  const [detailStatus, setDetailStatus] = useState<'idle' | 'loading' | 'error' | 'ready'>('idle')
  const statusWrite = useAdminWriteState()

  async function loadOrders(params: AdminOrderListParams) {
    setStatus('loading')
    try {
      const response = await listAdminOrders(params)
      setData(response)
      setStatus('ready')
      return response
    } catch {
      setStatus('error')
      return null
    }
  }

  useEffect(() => {
    let active = true
    void listAdminOrders({ page: 0, size: DEFAULT_PAGE_SIZE })
      .then((response) => {
        if (!active) return
        setData(response)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('error')
      })

    return () => {
      active = false
    }
  }, [])

  function buildFilterParams(page = 0): AdminOrderListParams {
    const search = filters.search.trim()
    return {
      status: filters.status || undefined,
      query: search || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page,
      size: data?.size ?? DEFAULT_PAGE_SIZE,
    }
  }

  function submitFilters(event: FormEvent) {
    event.preventDefault()
    setSelectedOrderId(null)
    setDetail(null)
    setDetailStatus('idle')
    statusWrite.reset()
    void loadOrders(buildFilterParams(0))
  }

  async function selectOrder(order: AdminOrderSummary) {
    setSelectedOrderId(order.id)
    setDetail(null)
    setDetailStatus('loading')
    try {
      const response = await getAdminOrderDetail(order.id)
      setDetail(response)
      setDetailStatus('ready')
    } catch {
      setDetailStatus('error')
    }
  }

  async function refreshSelectedOrder(orderId: string) {
    const response = await getAdminOrderDetail(orderId)
    setDetail(response)
    setDetailStatus('ready')
    return response
  }

  async function changeStatus(targetStatus: string) {
    if (!detail) return
    const confirmed = window.confirm(`Confirmar cambio de estado del pedido ${detail.reference} a ${statusLabel(targetStatus)}`)
    if (!confirmed) return

    statusWrite.start()
    try {
      const updated = await updateAdminOrderStatus(detail.id, targetStatus)
      setDetail(updated)
      await loadOrders(buildFilterParams(data?.page ?? 0))
      await refreshSelectedOrder(updated.id)
      statusWrite.succeed(`Estado del pedido actualizado a ${updated.status}.`)
    } catch (error) {
      statusWrite.fail(mapAdminWriteError(error))
    }
  }

  async function markPaid() {
    if (!detail) return
    const confirmed = window.confirm(`Confirmar marcar como pagado el pedido ${detail.reference}`)
    if (!confirmed) return

    statusWrite.start()
    try {
      const updated = await updateAdminOrderPaymentStatus(detail.id, 'PAID')
      setDetail(updated)
      await loadOrders(buildFilterParams(data?.page ?? 0))
      await refreshSelectedOrder(updated.id)
      statusWrite.succeed(`Pago confirmado manualmente para ${updated.reference}.`)
    } catch (error) {
      statusWrite.fail(mapAdminWriteError(error))
    }
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando pedidos…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar pedidos admin." />

  return (
    <section className="admin-page" aria-label="Listado de pedidos admin">
      <div className="admin-page-header">
        <p className="admin-eyebrow">Operaciones</p>
        <h2>Pedidos</h2>
        <p>Consulta pedidos y pagos para soporte operativo.</p>
      </div>

      <form className="admin-card" onSubmit={submitFilters} aria-label="Filtros de pedidos">
        <div className="admin-toolbar-grid">
          <label className="admin-field">
            <span>Estado pedido</span>
            <select
              value={filters.status}
              onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value }))}
              aria-label="Estado pedido"
            >
              {ORDER_STATUSES.map((option) => (
                <option key={option || 'all'} value={option}>{option || 'Todos'}</option>
              ))}
            </select>
          </label>
          <label className="admin-field">
            <span>Cliente o referencia</span>
            <input
              value={filters.search}
              onChange={(event) => setFilters((current) => ({ ...current, search: event.target.value }))}
              aria-label="Cliente o referencia"
              placeholder="Ana, ET-2026…"
            />
          </label>
          <label className="admin-field">
            <span>Fecha desde</span>
            <input
              type="date"
              value={filters.from}
              onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))}
              aria-label="Fecha desde"
            />
          </label>
          <label className="admin-field">
            <span>Fecha hasta</span>
            <input
              type="date"
              value={filters.to}
              onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))}
              aria-label="Fecha hasta"
            />
          </label>
        </div>
        <div className="admin-form-actions">
          <button className="btn btn-primary" type="submit">Filtrar pedidos</button>
        </div>
      </form>

      {!data || !data.items.length ? (
        <AdminEmptyState title="Sin pedidos" action={<button className="btn btn-secondary" type="button" onClick={() => void loadOrders(buildFilterParams(0))}>Actualizar lista</button>} />
      ) : (
        <>
          <p className="admin-card-help">Mostrando {data.items.length} de {data.totalElements} pedidos · página {data.page + 1} de {Math.max(data.totalPages, 1)}</p>
          <ul className="admin-list" aria-label="Pedidos encontrados">
            {data.items.map((order) => (
              <li className="admin-list-item" key={order.id}>
                <article className="admin-item-card" aria-label={`Pedido ${order.reference}`}>
                  <div className="admin-item-main">
                    <strong>{order.reference}</strong>
                    <span>{formatDate(order.createdAt)} · {order.customer}</span>
                    <span>Estado: <span className="admin-badge admin-badge-muted">{statusLabel(order.status)}</span></span>
                    <span>Pago: {order.paymentStatus || 'Sin detalle'}</span>
                    <span>Total: {formatMoney(order.total)}</span>
                  </div>
                  <div className="admin-item-actions">
                    <button className="btn btn-secondary" type="button" onClick={() => void selectOrder(order)}>
                      Ver detalle {order.reference}
                    </button>
                  </div>
                </article>
              </li>
            ))}
          </ul>
        </>
      )}

      <AdminWriteStateBanner feedback={statusWrite.feedback} onDismiss={statusWrite.reset} />

      {selectedOrderId ? renderDetailPanel(detailStatus, detail, statusWrite.isPending, changeStatus, markPaid) : null}
    </section>
  )
}

function renderDetailPanel(
  status: 'idle' | 'loading' | 'error' | 'ready',
  detail: AdminOrderDetailResponse | null,
  isPending: boolean,
  onChangeStatus: (targetStatus: string) => void,
  onMarkPaid: () => void,
) {
  if (status === 'loading') return <AdminLoadingState label="Cargando detalle del pedido…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar el detalle del pedido." />
  if (status !== 'ready' || !detail) return null

  return (
    <section className="admin-card" aria-label={`Detalle pedido ${detail.reference}`}>
      <div className="admin-card-header">
        <h3>Detalle {detail.reference}</h3>
        <p><span className="admin-badge admin-badge-muted">{statusLabel(detail.status)}</span> · {formatDate(detail.createdAt)}</p>
      </div>
      <section className="admin-card" aria-label="Siguiente acción del pedido">
        <div className="admin-card-header">
          <h4>Siguiente acción</h4>
          <p>{nextActionHelp(detail.status)}</p>
        </div>
        <div className="admin-form-actions" aria-label="Acciones disponibles del pedido">
          {canMarkPaid(detail) ? (
          <button
            aria-label="Marcar pago confirmado / Marcar como pagado"
            className="btn btn-primary"
            disabled={isPending}
            type="button"
            onClick={onMarkPaid}
          >
            Marcar pago confirmado
          </button>
          ) : null}
          {allowedStatusActions(detail.status).map((action) => (
            <button
              className={`btn btn-${action.variant}`}
              disabled={isPending}
              key={action.targetStatus}
              type="button"
              onClick={() => onChangeStatus(action.targetStatus)}
            >
              {action.label}
            </button>
          ))}
          {!canMarkPaid(detail) && !allowedStatusActions(detail.status).length ? (
            <p className="admin-card-help">Estado terminal: {statusLabel(detail.status)}. No hay acciones operativas pendientes.</p>
          ) : null}
        </div>
      </section>
      <div className="admin-toolbar-grid">
        <div>
          <h4>Cliente</h4>
          <p>{detail.customer}</p>
          {detail.phone ? <p>{detail.phone}</p> : null}
          {detail.note ? <p>Nota: {detail.note}</p> : null}
          <p>Entrega: {fulfillmentLabel(detail.fulfillmentMethod)}</p>
          {detail.fulfillmentMethod === 'DELIVERY' && detail.deliveryAddress ? <p>Dirección: {detail.deliveryAddress}</p> : null}
          {detail.fulfillmentMethod === 'PICKUP' && detail.pickupTime ? <p>Retiro: {detail.pickupTime}</p> : null}
        </div>
        <div>
          <h4>Pago</h4>
          <p>{detail.payment?.provider || 'Sin proveedor'}</p>
          <p>{detail.payment?.statusDetail || 'Sin estado de pago'}</p>
          {detail.payment?.externalId ? <p>ID externo: {detail.payment.externalId}</p> : null}
        </div>
      </div>
      <ul className="admin-list" aria-label="Items del pedido">
        {detail.items.map((item) => (
          <li key={item.id} className="admin-list-item">
            <article className="admin-item-card" aria-label={`Item ${item.productName}`}>
              <div className="admin-item-main">
                <strong>{item.productName}</strong>
                <span>{item.unitLabel} · Variante {item.variantId}</span>
                <span>{item.quantity} × {formatMoney(item.unitPrice)}</span>
              </div>
              <strong>{formatMoney(item.subtotal)}</strong>
            </article>
          </li>
        ))}
      </ul>
      <p><strong>Subtotal:</strong> {formatMoney(detail.subtotal)} · <strong>Total:</strong> {formatMoney(detail.total)} {detail.currency || ''}</p>
    </section>
  )
}

function fulfillmentLabel(method?: AdminOrderDetailResponse['fulfillmentMethod']) {
  return method === 'DELIVERY' ? 'Envío a domicilio' : 'Retiro en el local'
}

function canMarkPaid(detail: AdminOrderDetailResponse): boolean {
  if (detail.status !== 'PAYMENT_PENDING' && detail.status !== 'DRAFT') return false
  const paymentStatus = detail.payment?.statusDetail?.toLowerCase().trim()
  return paymentStatus !== 'approved' && paymentStatus !== 'manual_paid'
}

function allowedStatusActions(status: string): OrderStatusAction[] {
  if (status === 'DRAFT' || status === 'PAYMENT_PENDING') {
    return [
      { targetStatus: 'CANCELLED', label: 'Cancelar pedido', variant: 'secondary' },
      { targetStatus: 'EXPIRED', label: 'Expirar pedido', variant: 'secondary' },
    ]
  }
  if (status === 'PAID') return [{ targetStatus: 'PREPARING', label: 'Marcar como preparando', variant: 'primary' }]
  if (status === 'PREPARING') return [{ targetStatus: 'READY', label: 'Marcar listo para retirar', variant: 'primary' }]
  if (status === 'READY') return [{ targetStatus: 'DELIVERED', label: 'Marcar como entregado', variant: 'primary' }]
  return []
}

function nextActionHelp(status: string): string {
  if (status === 'DRAFT' || status === 'PAYMENT_PENDING') return 'Confirmá el pago manualmente o cerrá pedidos que no van a avanzar.'
  if (status === 'PAID') return 'El pago ya está confirmado; el próximo paso es preparar el pedido.'
  if (status === 'PREPARING') return 'El pedido está en preparación; marcá cuando quede listo para retirar.'
  if (status === 'READY') return 'El pedido está listo; marcá la entrega cuando el cliente lo retire.'
  if (TERMINAL_STATUSES.has(status)) return 'Este pedido ya está en un estado terminal.'
  return 'No hay acciones configuradas para este estado.'
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: 'Pendiente de confirmación',
    PAYMENT_PENDING: 'Pendiente de pago',
    PAID: 'Pago confirmado',
    PREPARING: 'Preparando',
    READY: 'Listo para retirar',
    DELIVERED: 'Entregado',
    FAILED: 'Pago fallido',
    CANCELLED: 'Cancelado',
    EXPIRED: 'Expirado',
  }
  return labels[status] ?? status
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function formatMoney(value: number | string): string {
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) return String(value)
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(numeric)
}
