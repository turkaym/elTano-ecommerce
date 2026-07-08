import { type FormEvent, useEffect, useRef, useState } from 'react'
import {
  createAdminCategory,
  listAdminCategories,
  listAdminProducts,
  mapAdminWriteError,
  updateAdminCategory,
  type AdminCategoryDto,
  type AdminProductDto,
} from '../services/adminOperationsService'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

export function AdminCategoriesPage() {
  const [items, setItems] = useState<AdminCategoryDto[]>([])
  const [products, setProducts] = useState<AdminProductDto[]>([])
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false)
  const write = useAdminWriteState()
  const editDialogRef = useRef<HTMLElement | null>(null)

  const editingCategory = editingId ? (items.find((item) => item.id === editingId) ?? null) : null

  useEffect(() => {
    let active = true
    void Promise.all([listAdminCategories(), listAdminProducts()])
      .then(([response, productResponse]) => {
        if (!active) return
        setItems(response)
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

  useEffect(() => {
    if (editingId) editDialogRef.current?.focus()
  }, [editingId])

  function resetDraft() {
    setName('')
    setSlug('')
    setSlugTouched(false)
    setEditingId(null)
  }

  function openCreateForm() {
    resetDraft()
    write.reset()
    setIsCreateFormOpen(true)
  }

  function closeCreateForm() {
    resetDraft()
    write.reset()
    setIsCreateFormOpen(false)
  }

  function openEditDialog(item: AdminCategoryDto) {
    setIsCreateFormOpen(false)
    setEditingId(item.id)
    setName(item.name)
    setSlug(item.slug)
    setSlugTouched(true)
    write.reset()
  }

  function closeEditDialog() {
    resetDraft()
    write.reset()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (write.isPending) return
    if (!name.trim()) {
      write.fail({ message: 'Nombre es requerido.' })
      return
    }
    if (!slug.trim()) {
      write.fail({ message: 'Slug es requerido.' })
      return
    }
    write.start()
    try {
      const payload = {
        name: name.trim(),
        slug: slug.trim(),
        active: editingCategory ? editingCategory.active !== false : true,
      }
      if (editingId) {
        await updateAdminCategory(editingId, payload)
      } else {
        await createAdminCategory(payload)
      }
      setItems(await listAdminCategories())
      setProducts(await listAdminProducts())
      resetDraft()
      setIsCreateFormOpen(false)
      write.succeed('Categoría guardada correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  async function toggleCategory(item: AdminCategoryDto) {
    if (write.isPending) return
    const isActive = item.active !== false
    if (isActive) {
      const activeProducts = countActiveProductsForCategory(products, item.id)
      if (activeProducts > 0) {
        write.fail({
          message: `No se puede desactivar la categoría porque tiene ${activeProducts} producto${activeProducts === 1 ? '' : 's'} activo${activeProducts === 1 ? '' : 's'} asociado${activeProducts === 1 ? '' : 's'}.`,
        })
        return
      }
    }

    const verb = isActive ? 'desactivar' : 'reactivar'
    if (!window.confirm(`¿Confirmás ${verb} la categoría ${item.name}?`)) return
    write.start()
    try {
      await updateAdminCategory(item.id, { name: item.name, slug: item.slug, active: !isActive })
      const [categories, productResponse] = await Promise.all([listAdminCategories(), listAdminProducts()])
      setItems(categories)
      setProducts(productResponse)
      write.succeed(isActive ? 'Categoría desactivada correctamente.' : 'Categoría reactivada correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando categorías…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar categorías admin." />
  return (
    <section className="admin-page" aria-label="Listado de categorías admin">
      <div className="admin-page-header">
        <div>
          <p className="admin-eyebrow">Catálogo</p>
          <h2>Categorías</h2>
          <p>Ordená el catálogo y controlá qué categorías permanecen activas.</p>
        </div>
        <button className="btn btn-primary" type="button" onClick={openCreateForm}>
          Crear nueva categoría
        </button>
      </div>
      {isCreateFormOpen ? (
        <section className="admin-card" aria-labelledby="category-form-title">
          <div className="admin-card-header admin-card-title-row">
            <div>
              <h3 id="category-form-title">Datos de categoría</h3>
              <p>Nombre público y slug utilizado en navegación.</p>
            </div>
            <button
              className="admin-card-close"
              type="button"
              aria-label="Cerrar formulario de categoría"
              onClick={closeCreateForm}
              disabled={write.isPending}
            >
              ×
            </button>
          </div>
          <CategoryForm
            name={name}
            slug={slug}
            isPending={write.isPending}
            submitLabel="Crear categoría"
            onSubmit={submit}
            onNameChange={(nextName) => {
              setName(nextName)
              if (!slugTouched) setSlug(slugify(nextName))
            }}
            onSlugChange={(nextSlug) => {
              setSlugTouched(true)
              setSlug(slugify(nextSlug))
            }}
          />
        </section>
      ) : null}
      <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />
      {!items.length ? <AdminEmptyState title="Sin categorías" action={null} /> : null}
      <section className="admin-card admin-toolbar" aria-labelledby="category-filters-title">
        <div className="admin-card-header">
          <h3 id="category-filters-title">Filtros de categorías</h3>
          <p>Mostrá todas, activas o inactivas.</p>
        </div>
      <label className="admin-field">
        <span>Filtrar categorías por estado</span>
        <select
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value as 'all' | 'active' | 'inactive')}
          aria-label="Filtrar categorías por estado"
        >
          <option value="all">Todas</option>
          <option value="active">Activas</option>
          <option value="inactive">Inactivas</option>
        </select>
      </label>
      </section>
      <ul className="admin-list admin-category-list" aria-label="Categorías admin">
        {visibleCategories(items, statusFilter).map((item) => (
          <li className="admin-list-item" key={item.id}>
            <article className="admin-item-card" aria-label={`Categoría ${item.name}`}>
            <div className="admin-item-main">
            <strong>{item.name}</strong>
            <span className={`admin-badge ${item.active === false ? 'admin-badge-muted' : 'admin-badge-success'}`}>Estado: {item.active === false ? 'Inactiva' : 'Activa'}</span>
            </div>
            <div className="admin-item-actions" role="group" aria-label={`Acciones de categoría ${item.name}`}>
            <button
              className="btn btn-secondary"
              type="button"
              onClick={() => openEditDialog(item)}
              aria-label={`Editar categoría ${item.name}`}
            >
              Editar
            </button>
            <button
              className={item.active === false ? 'btn btn-secondary' : 'btn btn-danger-soft'}
              type="button"
              onClick={() => void toggleCategory(item)}
              aria-label={item.active === false ? `Reactivar categoría ${item.name}` : `Desactivar categoría ${item.name}`}
            >
              {item.active === false ? 'Reactivar' : 'Desactivar'}
            </button>
            </div>
            </article>
          </li>
        ))}
      </ul>
      {editingCategory ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="category-edit-dialog-title"
            ref={editDialogRef}
            tabIndex={-1}
          >
            <div className="admin-dialog-header">
              <div>
                <p className="admin-eyebrow">Editar categoría</p>
                <h3 id="category-edit-dialog-title">Editar categoría {editingCategory.name}</h3>
                <p>Actualizá el nombre y slug sin abandonar la lista.</p>
              </div>
              <button
                className="admin-card-close"
                type="button"
                aria-label="Cerrar edición de categoría"
                onClick={closeEditDialog}
                disabled={write.isPending}
              >
                ×
              </button>
            </div>
            <CategoryForm
              name={name}
              slug={slug}
              isPending={write.isPending}
              submitLabel="Actualizar categoría"
              onSubmit={submit}
              onNameChange={(nextName) => {
                setName(nextName)
                if (!slugTouched) setSlug(slugify(nextName))
              }}
              onSlugChange={(nextSlug) => {
                setSlugTouched(true)
                setSlug(slugify(nextSlug))
              }}
            />
          </section>
        </div>
      ) : null}
    </section>
  )
}

type CategoryFormProps = {
  name: string
  slug: string
  isPending: boolean
  submitLabel: string
  onSubmit: (event: FormEvent) => void
  onNameChange: (value: string) => void
  onSlugChange: (value: string) => void
}

function CategoryForm({ name, slug, isPending, submitLabel, onSubmit, onNameChange, onSlugChange }: CategoryFormProps) {
  return (
    <form className="admin-form" onSubmit={onSubmit}>
      <div className="admin-form-grid">
        <label className="admin-field">
          <span>Nombre</span>
          <input value={name} onChange={(event) => onNameChange(event.target.value)} aria-label="Nombre categoría" />
        </label>
        <label className="admin-field">
          <span>Slug</span>
          <input value={slug} onChange={(event) => onSlugChange(event.target.value)} aria-label="Slug categoría" />
        </label>
      </div>
      <div className="admin-form-actions">
        <button className="btn btn-primary" type="submit" disabled={isPending}>
          {submitLabel}
        </button>
      </div>
    </form>
  )
}

function visibleCategories(items: AdminCategoryDto[], statusFilter: 'all' | 'active' | 'inactive') {
  return items.filter((item) => {
    if (statusFilter === 'active') return item.active !== false
    if (statusFilter === 'inactive') return item.active === false
    return true
  })
}

function countActiveProductsForCategory(products: AdminProductDto[], categoryId: string): number {
  return products.filter((product) => product.categoryId === categoryId && product.active !== false && !product.deletedAt).length
}

function slugify(input: string): string {
  const normalized = input
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || 'categoria'
}
