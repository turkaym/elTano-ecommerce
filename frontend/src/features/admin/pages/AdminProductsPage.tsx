import { type FormEvent, useEffect, useState } from 'react'
import {
  createAdminProduct,
  listAdminProducts,
  mapAdminWriteError,
  updateAdminProduct,
  type AdminProductDto,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

export function AdminProductsPage() {
  const [items, setItems] = useState<AdminProductDto[]>([])
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [name, setName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const write = useAdminWriteState()

  useEffect(() => {
    let active = true
    void listAdminProducts()
      .then((response) => {
        if (!active) return
        setItems(response)
        setStatus('ready')
      })
      .catch(() => {
        if (active) setStatus('error')
      })

    return () => {
      active = false
    }
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (write.isPending) return
    if (!name.trim()) {
      write.fail({ message: 'Nombre es requerido.', fieldErrors: [{ field: 'name', message: 'Nombre es requerido.' }] })
      return
    }

    write.start()
    try {
      if (editingId) {
        await updateAdminProduct(editingId, { name: name.trim() })
      } else {
        await createAdminProduct({ name: name.trim() })
      }
      setItems(await listAdminProducts())
      setName('')
      setEditingId(null)
      write.succeed('Producto guardado correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando productos…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar productos admin." />

  if (!items.length) {
    return <AdminEmptyState title="Sin productos" action={<button type="button">Crear producto</button>} />
  }

  return (
    <section aria-label="Listado de productos admin">
      <h2>Productos</h2>
      <form onSubmit={submit}>
        <label>
          Nombre
          <input value={name} onChange={(event) => setName(event.target.value)} aria-label="Nombre producto" />
        </label>
        <button type="submit" disabled={write.isPending}>
          {editingId ? 'Actualizar producto' : 'Crear producto'}
        </button>
      </form>
      <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            {item.name}
            <button
              type="button"
              onClick={() => {
                setEditingId(item.id)
                setName(item.name)
                write.reset()
              }}
            >
              Editar
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
