import { type FormEvent, useEffect, useState } from 'react'
import {
  createAdminCategory,
  listAdminCategories,
  mapAdminWriteError,
  updateAdminCategory,
  type AdminCategoryDto,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

export function AdminCategoriesPage() {
  const [items, setItems] = useState<AdminCategoryDto[]>([])
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [name, setName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const write = useAdminWriteState()

  useEffect(() => {
    let active = true
    void listAdminCategories()
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
      write.fail({ message: 'Nombre es requerido.' })
      return
    }
    write.start()
    try {
      if (editingId) {
        await updateAdminCategory(editingId, { name: name.trim() })
      } else {
        await createAdminCategory({ name: name.trim() })
      }
      setItems(await listAdminCategories())
      setName('')
      setEditingId(null)
      write.succeed('Categoría guardada correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando categorías…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar categorías admin." />
  if (!items.length) {
    return <AdminEmptyState title="Sin categorías" action={<button type="button">Crear categoría</button>} />
  }

  return (
    <section aria-label="Listado de categorías admin">
      <h2>Categorías</h2>
      <form onSubmit={submit}>
        <label>
          Nombre
          <input value={name} onChange={(event) => setName(event.target.value)} aria-label="Nombre categoría" />
        </label>
        <button type="submit" disabled={write.isPending}>
          {editingId ? 'Actualizar categoría' : 'Crear categoría'}
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
