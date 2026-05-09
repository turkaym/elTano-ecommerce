import { useEffect, useState } from 'react'
import { listAdminOrders, type AdminOrderListResponse } from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState } from './AdminPageStates'

export function AdminOrdersPage() {
  const [data, setData] = useState<AdminOrderListResponse | null>(null)
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')

  useEffect(() => {
    listAdminOrders({ page: 0, size: 20 })
      .then((response) => {
        setData(response)
        setStatus('ready')
      })
      .catch(() => setStatus('error'))
  }, [])

  if (status === 'loading') return <AdminLoadingState label="Cargando pedidos…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar pedidos admin." />

  if (!data || !data.items.length) {
    return <AdminEmptyState title="Sin pedidos" action={<button type="button">Actualizar lista</button>} />
  }

  return (
    <section aria-label="Listado de pedidos admin">
      <h2>Pedidos</h2>
      <ul>
        {data.items.map((order) => (
          <li key={order.id}>{order.reference} — {order.status}</li>
        ))}
      </ul>
    </section>
  )
}
