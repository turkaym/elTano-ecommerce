import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  listAdminOrders,
  listAdminProducts,
  type AdminOrderSummary,
  type AdminProductDto,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState } from './AdminPageStates'

const RECENT_ORDERS_SIZE = 8
const LOW_STOCK_UNITS_THRESHOLD = 5
const LOW_STOCK_GRAMS_THRESHOLD = 5_000
const CLOSED_ORDER_STATUSES = new Set(['CANCELLED', 'EXPIRED', 'FAILED', 'DELIVERED'])

interface StockAlert {
  productId: string
  productName: string
  severity: 'out' | 'low'
  label: string
}

export function AdminDashboardPage() {
  const [orders, setOrders] = useState<AdminOrderSummary[]>([])
  const [products, setProducts] = useState<AdminProductDto[]>([])
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')

  useEffect(() => {
    let active = true
    void Promise.all([listAdminOrders({ page: 0, size: RECENT_ORDERS_SIZE }), listAdminProducts()])
      .then(([orderResponse, productResponse]) => {
        if (!active) return
        setOrders(orderResponse.items)
        setProducts(productResponse)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('error')
      })

    return () => {
      active = false
    }
  }, [])

  const summary = useMemo(() => buildDashboardSummary(orders, products, new Date()), [orders, products])

  if (status === 'loading') return <AdminLoadingState label="Cargando dashboard admin…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar el dashboard admin." />

  return (
    <section className="admin-page" aria-label="Dashboard principal admin">
      <div className="admin-page-header">
        <p className="admin-eyebrow">Operación diaria</p>
        <h2>Dashboard admin</h2>
        <p>Métricas, alertas y accesos rápidos para priorizar el trabajo del día.</p>
      </div>

      <section className="admin-dashboard-metrics" aria-label="Métricas principales">
        <MetricCard label="Pedidos de hoy" value={summary.todayOrders} helper="Órdenes creadas desde las 00:00" />
        <MetricCard label="Ventas confirmadas de hoy" value={formatMoney(summary.todayConfirmedSales)} helper="Pagos aprobados o confirmados" />
        <MetricCard label="Pendientes de pago" value={summary.pendingOrders} helper="Requieren revisión de cobro" />
        <MetricCard label="Pedidos para preparar" value={summary.ordersToPrepare} helper="Pagados y todavía abiertos" />
        <MetricCard label="Productos sin stock" value={summary.outOfStockProducts} helper="No se pueden vender" />
        <MetricCard label="Productos con stock bajo" value={summary.lowStockProducts} helper="Reponer pronto" />
      </section>

      <section className="admin-card" aria-label="Alertas y atención">
        <div className="admin-card-header">
          <h3>Alertas y atención</h3>
          <p>Productos críticos y pedidos que necesitan confirmación.</p>
        </div>
        {summary.stockAlerts.length || summary.pendingPaymentOrders.length ? (
          <ul className="admin-list">
            {summary.stockAlerts.map((alert) => (
              <li className="admin-list-item" key={`${alert.productId}-${alert.label}`}>
                <article className="admin-item-card" aria-label={`Alerta stock ${alert.productName}`}>
                  <div className="admin-item-main">
                    <strong>{alert.productName}</strong>
                    <span className={`admin-badge ${alert.severity === 'out' ? 'admin-badge-danger' : 'admin-badge-muted'}`}>
                      {alert.severity === 'out' ? 'Sin stock' : 'Stock bajo'}
                    </span>
                    <span>{alert.label}</span>
                  </div>
                  <Link className="btn btn-secondary" to="/admin/productos">Revisar producto</Link>
                </article>
              </li>
            ))}
            {summary.pendingPaymentOrders.map((order) => (
              <li className="admin-list-item" key={order.id}>
                <article className="admin-item-card" aria-label={`Alerta pedido ${order.reference}`}>
                  <div className="admin-item-main">
                    <strong>{order.reference}</strong>
                    <span>{order.customer} · {formatDate(order.createdAt)}</span>
                    <span>Estado: {order.status} · Pago: {order.paymentStatus || 'Sin detalle'}</span>
                  </div>
                  <Link className="btn btn-secondary" to="/admin/pedidos">Revisar pedido {order.reference}</Link>
                </article>
              </li>
            ))}
          </ul>
        ) : (
          <AdminEmptyState title="Sin alertas críticas" action={<p>No hay pagos pendientes ni stock crítico con los datos actuales.</p>} />
        )}
      </section>

      <section className="admin-card" aria-label="Pedidos recientes">
        <div className="admin-card-header admin-card-header-actions">
          <div>
            <h3>Pedidos recientes</h3>
            <p>Últimos movimientos para seguimiento operativo.</p>
          </div>
          <Link className="btn btn-secondary" to="/admin/pedidos">Ver todos los pedidos</Link>
        </div>
        {orders.length ? (
          <ul className="admin-list">
            {orders.map((order) => (
              <li className="admin-list-item" key={order.id}>
                <article className="admin-item-card" aria-label={`Pedido ${order.reference}`}>
                  <div className="admin-item-main">
                    <strong>{order.reference}</strong>
                    <span>{order.customer} · {formatDate(order.createdAt)}</span>
                    <span>Estado: <span className="admin-badge admin-badge-muted">{order.status}</span></span>
                    <span>Pago: {order.paymentStatus || 'Sin detalle'}</span>
                  </div>
                  <strong>{formatMoney(order.total)}</strong>
                </article>
              </li>
            ))}
          </ul>
        ) : (
          <AdminEmptyState title="Sin pedidos recientes" action={<p>Todavía no hay pedidos para mostrar.</p>} />
        )}
      </section>

      <nav className="admin-card admin-quick-links" aria-label="Accesos rápidos admin">
        <Link className="btn btn-primary" to="/admin/productos">Productos</Link>
        <Link className="btn btn-secondary" to="/admin/categorias">Categorías</Link>
        <Link className="btn btn-secondary" to="/admin/pedidos">Pedidos</Link>
        <Link className="btn btn-secondary" to="/admin/catalog-jobs">Catalog Jobs</Link>
      </nav>
    </section>
  )
}

function MetricCard({ label, value, helper }: { label: string; value: number | string; helper: string }) {
  return (
    <article className="admin-card admin-metric-card" aria-label={label}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{helper}</small>
    </article>
  )
}

function buildDashboardSummary(orders: AdminOrderSummary[], products: AdminProductDto[], now: Date) {
  const todayOrders = orders.filter((order) => isSameLocalDay(new Date(order.createdAt), now))
  const stockAlerts = products.flatMap(buildStockAlerts)
  return {
    todayOrders: todayOrders.length,
    todayConfirmedSales: todayOrders.filter(isPaymentConfirmed).reduce((total, order) => total + toNumber(order.total), 0),
    pendingOrders: orders.filter(isPendingPaymentOrder).length,
    ordersToPrepare: orders.filter(isOrderToPrepare).length,
    outOfStockProducts: stockAlerts.filter((alert) => alert.severity === 'out').length,
    lowStockProducts: stockAlerts.filter((alert) => alert.severity === 'low').length,
    stockAlerts,
    pendingPaymentOrders: orders.filter(isPendingPaymentOrder),
  }
}

function buildStockAlerts(product: AdminProductDto): StockAlert[] {
  if (product.active === false || product.deletedAt) return []

  if (product.inventoryPolicy === 'BULK_WEIGHT' || product.productType === 'GRANEL') {
    const availableGrams = Math.max(0, (product.stockBaseGrams ?? 0) - (product.stockReservedBaseGrams ?? 0))
    if (availableGrams <= 0) return [{ productId: product.id, productName: product.name, severity: 'out', label: 'Sin stock granel disponible' }]
    if (availableGrams <= LOW_STOCK_GRAMS_THRESHOLD) {
      return [{ productId: product.id, productName: product.name, severity: 'low', label: `${formatWeight(availableGrams)} disponibles` }]
    }
    return []
  }

  const variants = product.variants ?? []
  const outVariant = variants.find((variant) => (variant.stockAvailable ?? 0) <= 0)
  if (outVariant) {
    return [{ productId: product.id, productName: product.name, severity: 'out', label: `${outVariant.unitLabel || 'Variante'} · Sin stock` }]
  }
  const lowVariant = variants.find((variant) => (variant.stockAvailable ?? 0) <= LOW_STOCK_UNITS_THRESHOLD)
  if (lowVariant) {
    return [{ productId: product.id, productName: product.name, severity: 'low', label: `${lowVariant.unitLabel || 'Variante'} · ${lowVariant.stockAvailable ?? 0} disponibles` }]
  }
  return []
}

function isPendingPaymentOrder(order: AdminOrderSummary): boolean {
  if (CLOSED_ORDER_STATUSES.has(order.status)) return false
  if (order.status === 'PAYMENT_PENDING' || order.status === 'DRAFT') return true
  const paymentStatus = order.paymentStatus?.toLowerCase().trim()
  return !paymentStatus || ['pending', 'in_process', 'manual_pending'].includes(paymentStatus)
}

function isOrderToPrepare(order: AdminOrderSummary): boolean {
  return !CLOSED_ORDER_STATUSES.has(order.status) && (order.status === 'PAID' || isPaymentConfirmed(order))
}

function isPaymentConfirmed(order: AdminOrderSummary): boolean {
  const paymentStatus = order.paymentStatus?.toLowerCase().trim()
  return order.status === 'PAID' || paymentStatus === 'approved' || paymentStatus === 'manual_paid'
}

function isSameLocalDay(value: Date, reference: Date): boolean {
  return value.getFullYear() === reference.getFullYear() && value.getMonth() === reference.getMonth() && value.getDate() === reference.getDate()
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function formatMoney(value: number | string): string {
  const numeric = toNumber(value)
  if (!Number.isFinite(numeric)) return String(value)
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(numeric)
}

function formatWeight(grams: number): string {
  if (grams >= 1000) {
    const kg = grams / 1000
    return `${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(kg)} kg`
  }
  return `${new Intl.NumberFormat('es-AR').format(grams)} g`
}

function toNumber(value: number | string): number {
  return typeof value === 'number' ? value : Number(value)
}
