import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react'
import {
  type AdminCategoryDto,
  createAdminProduct,
  deleteAdminProduct,
  listAdminCategories,
  listAdminProducts,
  mapAdminWriteError,
  restoreAdminProduct,
  updateAdminProduct,
  type AdminProductDto,
  uploadAdminProductImage,
} from '../services/adminOperationsService'
import { getAdminStockState } from '../stock/adminStockState'
import { AdminEmptyState, AdminErrorState, AdminLoadingState, AdminWriteStateBanner } from './AdminPageStates'
import { useAdminWriteState } from './adminWriteState'

type VariantUnit = 'g' | 'kg' | 'ml' | 'l' | 'unidad'
type StockFilter = 'all' | 'low' | 'out'
type ProductType = 'GRANEL' | 'ENVASADO' | 'UNIDAD'
type InventoryPolicy = 'BULK_WEIGHT' | 'PER_VARIANT'

interface ProductVariantFormRow {
  id?: string
  sku: string
  amount: string
  unit: VariantUnit
  price: string
  stock: string
  stockReserved: number
  active: boolean
  attributesJson?: string | null
}

interface ProductFormDraft {
  name: string
  slug: string
  slugTouched: boolean
  description: string
  imageUrl: string
  imageAltText: string
  imageId?: string
  imagePreviewBroken: boolean
  variants: ProductVariantFormRow[]
  selectedCategoryId: string
  stockBaseGrams: string
  pricePerKg: string
}

const DEFAULT_VARIANT: ProductVariantFormRow = {
  sku: '',
  amount: '100',
  unit: 'g',
  price: '',
  stock: '0',
  stockReserved: 0,
  active: true,
}

const VARIANT_PRESETS: { label: string; amount: string; unit: VariantUnit }[] = [
  { label: '100g', amount: '100', unit: 'g' },
  { label: '250g', amount: '250', unit: 'g' },
  { label: '500g', amount: '500', unit: 'g' },
  { label: '1kg', amount: '1', unit: 'kg' },
  { label: '500ml', amount: '500', unit: 'ml' },
  { label: '1l', amount: '1', unit: 'l' },
  { label: '2l', amount: '2', unit: 'l' },
  { label: 'unidad', amount: '1', unit: 'unidad' },
]

const GRANEL_FIXED_PRESENTATIONS = [
  { label: '100g', weightGrams: 100 },
  { label: '250g', weightGrams: 250 },
  { label: '500g', weightGrams: 500 },
  { label: '1kg', weightGrams: 1000 },
]

export function AdminProductsPage() {
  const [items, setItems] = useState<AdminProductDto[]>([])
  const [categories, setCategories] = useState<AdminCategoryDto[]>([])
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [description, setDescription] = useState('')
  const [imageUrl, setImageUrl] = useState('')
  const [imageAltText, setImageAltText] = useState('')
  const [imageId, setImageId] = useState<string | undefined>()
  const [imagePreviewBroken, setImagePreviewBroken] = useState(false)
  const [variants, setVariants] = useState<ProductVariantFormRow[]>([{ ...DEFAULT_VARIANT }])
  const [selectedCategoryId, setSelectedCategoryId] = useState('')
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all')
  const [categoryFilter, setCategoryFilter] = useState('all')
  const [stockFilter, setStockFilter] = useState<StockFilter>('all')
  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<AdminProductDto | null>(null)
  const [editDraft, setEditDraft] = useState<ProductFormDraft | null>(null)
  const [stockBaseGrams, setStockBaseGrams] = useState('')
  const [pricePerKg, setPricePerKg] = useState('')
  const editNameRef = useRef<HTMLInputElement>(null)
  const editDialogRef = useRef<HTMLElement>(null)
  const editTriggerRef = useRef<HTMLButtonElement | null>(null)
  const write = useAdminWriteState()

  useEffect(() => {
    if (editDraft) editNameRef.current?.focus()
  }, [editDraft])

  useEffect(() => {
    let active = true
    void Promise.all([listAdminProducts(), listAdminCategories()])
      .then(([products, availableCategories]) => {
        if (!active) return
        setItems(products)
        setCategories(availableCategories)
        setSelectedCategoryId((current) => current || availableCategories[0]?.id || '')
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
    if (!categories.length) {
      write.fail({ message: 'Primero crea una categoría para poder guardar productos.' })
      return
    }

    const categoryId = selectedCategoryId
    if (!categoryId) {
      write.fail({ message: 'No se pudo determinar la categoría del producto.' })
      return
    }

    const normalizedName = name.trim()
    const normalizedSlug = slug.trim() || slugify(normalizedName)
    const normalizedDescription = description.trim()
    const resolvedProductType = resolveProductType(variants)
    const shouldGenerateGranelVariants = resolvedProductType === 'GRANEL' && Number(pricePerKg) > 0
    const validationErrors = validateProductForm({
      description: normalizedDescription,
      imageUrl: imageUrl.trim(),
      variants,
      skipVariantPrice: shouldGenerateGranelVariants,
    })
    if (validationErrors.length) {
      write.fail({ message: 'Revisá los datos del producto.', fieldErrors: validationErrors })
      return
    }

    const productType = resolvedProductType
    const inventoryPolicy = resolveInventoryPolicy(productType)
    const payloadVariants = shouldGenerateGranelVariants
      ? buildFixedGranelVariantPayloads(normalizedSlug, Number(pricePerKg))
      : variants.map((variant, index) => buildVariantPayload(variant, normalizedSlug, index))
    const normalizedImageUrl = imageUrl.trim()
    const payload = {
      name: normalizedName,
      slug: normalizedSlug,
      description: normalizedDescription,
      active: true,
      categoryId,
      productType,
      inventoryPolicy,
      stockBaseGrams: resolveStockBaseGrams(inventoryPolicy, payloadVariants, stockBaseGrams),
      variants: payloadVariants,
      images: normalizedImageUrl
        ? [
            {
              id: imageId,
              url: normalizedImageUrl,
              altText: imageAltText.trim() || normalizedName,
              sortOrder: 0,
              primary: true,
            },
          ]
        : [],
    }

    write.start()
    try {
      await createAdminProduct(payload)
      const [products, availableCategories] = await Promise.all([listAdminProducts(), listAdminCategories()])
      setItems(products)
      setCategories(availableCategories)
      setSelectedCategoryId(availableCategories[0]?.id || '')
      resetForm(availableCategories[0]?.id || '')
      write.succeed('Producto guardado correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  async function toggleProduct(item: AdminProductDto) {
    if (write.isPending) return
    const isActive = isProductActive(item)
    const verb = isActive ? 'desactivar' : 'reactivar'
    if (!window.confirm(`¿Confirmás ${verb} el producto ${item.name}?`)) return

    write.start()
    try {
      if (isActive) {
        await deleteAdminProduct(item.id)
      } else {
        await restoreAdminProduct(item.id)
      }
      const [products, availableCategories] = await Promise.all([listAdminProducts(), listAdminCategories()])
      setItems(products)
      setCategories(availableCategories)
      setSelectedCategoryId((current) => current || availableCategories[0]?.id || '')
      write.succeed(isActive ? 'Producto desactivado correctamente.' : 'Producto reactivado correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  async function handleImageUpload(file: File | undefined) {
    if (!file || write.isPending) return

    write.start()
    try {
      const result = await uploadAdminProductImage(file)
      setImageUrl(result.url)
      setImagePreviewBroken(false)
      write.succeed('Imagen subida correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  if (status === 'loading') return <AdminLoadingState label="Cargando productos…" />
  if (status === 'error') return <AdminErrorState message="No se pudo cargar productos admin." />

  const currentProductType = resolveProductType(variants)
  const isGranelProduct = currentProductType === 'GRANEL'
  const filteredProducts = visibleProducts(items, statusFilter, categoryFilter, stockFilter)

  return (
    <section className="admin-page" aria-label="Listado de productos admin">
      <div className="admin-page-header">
        <div>
          <p className="admin-eyebrow">Catálogo</p>
          <h2>Productos</h2>
          <p>Creá, editá y mantené variantes, imágenes y disponibilidad sin salir del panel.</p>
        </div>
        <button className="btn btn-primary" type="button" onClick={() => setIsCreateFormOpen(true)}>
          Crear nuevo producto
        </button>
      </div>
      {isCreateFormOpen ? <form className="admin-form" onSubmit={submit} noValidate>
        <section className="admin-card" aria-labelledby="product-basics-title">
          <div className="admin-card-header">
            <h3 id="product-basics-title">Básicos del producto</h3>
            <p>Datos visibles y categorización principal.</p>
          </div>
          <div className="admin-form-grid">
            <label className="admin-field">
              <span>Nombre</span>
              <input
            value={name}
            onChange={(event) => {
              setName(event.target.value)
              if (!slugTouched) setSlug(slugify(event.target.value))
            }}
            aria-label="Nombre producto"
          />
            </label>
            <label className="admin-field">
              <span>Slug producto</span>
              <input
            value={slug}
            onChange={(event) => {
              setSlugTouched(true)
              setSlug(slugify(event.target.value))
            }}
            aria-label="Slug producto"
          />
            </label>
            <label className="admin-field">
              <span>Categoría producto</span>
              <select
            value={selectedCategoryId}
            onChange={(event) => setSelectedCategoryId(event.target.value)}
            aria-label="Categoría producto"
          >
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
            </select>
            </label>
            <label className="admin-field admin-field-wide">
              <span>Descripción producto</span>
              <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            aria-label="Descripción producto"
          />
            </label>
          </div>
        </section>
        <fieldset className="admin-card admin-fieldset">
          <legend>Imagen principal</legend>
          <p className="admin-card-help">Subí una imagen JPG, PNG o WebP, o pegá una URL pública manualmente.</p>
          <div className="admin-form-grid">
          <label className="admin-field">
            <span>Subir imagen principal</span>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => void handleImageUpload(event.target.files?.[0])}
              aria-label="Subir imagen principal"
            />
          </label>
          <label className="admin-field">
            <span>URL imagen principal</span>
            <input
              value={imageUrl}
              onChange={(event) => {
                setImageUrl(event.target.value)
                setImagePreviewBroken(false)
              }}
              aria-label="URL imagen principal"
            />
          </label>
          <label className="admin-field">
            <span>Texto alternativo imagen</span>
            <input
              value={imageAltText}
              onChange={(event) => setImageAltText(event.target.value)}
              aria-label="Texto alternativo imagen"
            />
          </label>
          </div>
          {imageUrl.trim() && isValidImageUrl(imageUrl.trim()) ? (
            <div className="admin-image-preview">
              <p>Vista previa imagen principal</p>
              <img
                src={resolveImagePreviewSrc(imageUrl.trim())}
                alt={imageAltText.trim() || name.trim() || 'Vista previa imagen principal'}
                onError={() => setImagePreviewBroken(true)}
              />
              {imagePreviewBroken ? <p>No se pudo cargar la vista previa de la imagen.</p> : null}
            </div>
          ) : null}
        </fieldset>
        <fieldset className="admin-card admin-fieldset">
          <legend>Variantes</legend>
          <p className="admin-card-help">Separá precios, cantidades y stock por presentación.</p>
          {isGranelProduct ? (
            <div className="admin-card-help" aria-label="Stock granel compartido">
              <p>El stock granel se administra una sola vez y las presentaciones comparten ese stock base.</p>
              <div className="admin-form-grid">
                <label className="admin-field">
                  <span>Stock total granel en gramos</span>
                  <input
                    type="number"
                    min="0"
                    step="1"
                    value={stockBaseGrams}
                    onChange={(event) => setStockBaseGrams(event.target.value)}
                    aria-label="Stock total granel en gramos"
                  />
                </label>
                <label className="admin-field">
                  <span>Precio por kg granel</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={pricePerKg}
                    onChange={(event) => setPricePerKg(event.target.value)}
                    aria-label="Precio por kg granel"
                  />
                </label>
              </div>
              <div className="admin-form-grid" aria-label="Precios calculados granel">
                {GRANEL_FIXED_PRESENTATIONS.map((presentation, index) => (
                  <div className="admin-field" key={presentation.label} aria-label={`Precio calculado variante ${index + 1}`}>
                    <span>{presentation.label}</span>
                    <strong>{formatCurrency(calculateGranelPrice(Number(pricePerKg), presentation.weightGrams))}</strong>
                    <small>Precio calculado desde precio por kg granel.</small>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
          <div className="admin-preset-row" aria-label="Presets de variantes">
            {VARIANT_PRESETS.map((preset) => (
              <button className="btn btn-secondary admin-chip" key={preset.label} type="button" onClick={() => applyVariantPreset(preset)}>
                Preset {preset.label}
              </button>
            ))}
          </div>
          {variants.map((variant, index) => (
            <div className="admin-variant-row" key={`${variant.id ?? 'new'}-${index}`}>
              <h3>Variante {index + 1}</h3>
              <div className="admin-form-grid admin-variant-grid">
              <label className="admin-field">
                <span>SKU variante {index + 1}</span>
                <input
                  value={variant.sku}
                  onChange={(event) => updateVariant(index, { sku: event.target.value })}
                  aria-label={`SKU variante ${index + 1}`}
                />
              </label>
              <label className="admin-field">
                <span>Cantidad variante {index + 1}</span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={variant.amount}
                  onChange={(event) => updateVariant(index, { amount: event.target.value })}
                  aria-label={`Cantidad variante ${index + 1}`}
                />
              </label>
              <label className="admin-field">
                <span>Unidad variante {index + 1}</span>
                <select
                  value={variant.unit}
                  onChange={(event) => updateVariant(index, { unit: event.target.value as VariantUnit })}
                  aria-label={`Unidad variante ${index + 1}`}
                >
                  <option value="g">g</option>
                  <option value="kg">kg</option>
                  <option value="ml">ml</option>
                  <option value="l">l</option>
                  <option value="unidad">unidad</option>
                </select>
              </label>
              {isGranelProduct ? (
                <div className="admin-field" aria-label={`Precio calculado presentación variante ${index + 1}`}>
                  <span>Precio calculado variante {index + 1}</span>
                  <strong>{formatCurrency(calculateVariantPriceFromKg(Number(pricePerKg), variant))}</strong>
                  <small>El stock de esta presentación sale del stock granel base.</small>
                </div>
              ) : (
                <>
                  <label className="admin-field">
                    <span>Precio variante {index + 1}</span>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={variant.price}
                      onChange={(event) => updateVariant(index, { price: event.target.value })}
                      aria-label={`Precio variante ${index + 1}`}
                    />
                  </label>
                  <label className="admin-field">
                    <span>Stock variante {index + 1}</span>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      value={variant.stock}
                      onChange={(event) => updateVariant(index, { stock: event.target.value })}
                      aria-label={`Stock variante ${index + 1}`}
                    />
                  </label>
                </>
              )}
              <label className="admin-field admin-checkbox-field">
                <span>Activa variante {index + 1}</span>
                <input
                  type="checkbox"
                  checked={variant.active}
                  onChange={(event) => updateVariant(index, { active: event.target.checked })}
                  aria-label={`Activa variante ${index + 1}`}
                />
              </label>
              </div>
              <button className="btn btn-danger-soft" type="button" onClick={() => removeVariant(index)}>
                Quitar variante {index + 1}
              </button>
            </div>
          ))}
          <button className="btn btn-secondary" type="button" onClick={addVariant}>
            Agregar variante
          </button>
        </fieldset>
        <div className="admin-form-actions">
        <button className="btn btn-primary" type="submit" disabled={write.isPending}>
          Crear producto
        </button>
        </div>
      </form> : null}
      <AdminWriteStateBanner feedback={write.feedback} onDismiss={write.reset} onRetry={write.reset} />
      {!items.length ? <AdminEmptyState title="Sin productos" action={null} /> : null}
      <section className="admin-card admin-toolbar" aria-labelledby="product-filters-title">
        <div className="admin-card-header">
          <h3 id="product-filters-title">Filtros de productos</h3>
          <p>Acotá la lista por estado, categoría o stock.</p>
        </div>
        <div className="admin-toolbar-grid">
        <label className="admin-field">
          <span>Filtrar productos por estado</span>
          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value as 'all' | 'active' | 'inactive')}
            aria-label="Filtrar productos por estado"
          >
            <option value="all">Todos</option>
            <option value="active">Activos</option>
            <option value="inactive">Inactivos</option>
          </select>
        </label>
        <label className="admin-field">
          <span>Filtrar productos por categoría</span>
          <select
            value={categoryFilter}
            onChange={(event) => setCategoryFilter(event.target.value)}
            aria-label="Filtrar productos por categoría"
          >
            <option value="all">Todas</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="admin-field">
          <span>Filtrar productos por stock</span>
          <select value={stockFilter} onChange={(event) => setStockFilter(event.target.value as StockFilter)} aria-label="Filtrar productos por stock">
            <option value="all">Todo stock</option>
            <option value="low">Stock bajo o sin stock</option>
            <option value="out">Sin stock</option>
          </select>
        </label>
        </div>
      </section>
      <section className="admin-card admin-products-results" aria-labelledby="products-results-title">
        <div className="admin-card-header admin-card-header-actions">
          <div>
            <h3 id="products-results-title">Productos encontrados</h3>
            <p>{filteredProducts.length} productos visibles según los filtros actuales.</p>
          </div>
        </div>
        <ul className="admin-list admin-product-list" aria-label="Productos admin">
          {filteredProducts.map((item) => {
            const imageUrl = primaryImageUrl(item)
            const stockState = getAdminStockState(item)
            const active = isProductActive(item)
            return (
              <li className="admin-list-item" key={item.id}>
                <article className="admin-item-card admin-product-card" aria-label={`Producto ${item.name}`}>
                  <div className="admin-product-media" aria-label={`Imagen de ${item.name}`}>
                    {imageUrl === 'Sin imagen' ? (
                      <div className="admin-product-media-placeholder">
                        <strong>Sin imagen</strong>
                        <span>Podés cargarla desde el editor.</span>
                      </div>
                    ) : (
                      <img src={imageUrl} alt={item.images?.find((image) => image.primary)?.altText || item.images?.[0]?.altText || item.name} />
                    )}
                  </div>
                  <div className="admin-item-main">
                    <strong>{item.name}</strong>
                    <ul className="admin-product-meta-list" aria-label={`Datos clave de ${item.name}`}>
                      <li>Categoría: {resolveCategoryName(item, categories)}</li>
                      <li>
                        <span className={`admin-badge ${active ? 'admin-badge-success' : 'admin-badge-muted'}`}>Estado: {active ? 'Activo' : 'Inactivo'}</span>
                      </li>
                      <li>
                        <span className="admin-badge admin-badge-muted">Stock: {stockState.badgeLabel}</span>
                      </li>
                      <li>Imagen: {imageUrl}</li>
                    </ul>
                    <ul className="admin-product-variant-list" aria-label={`Presentaciones de ${item.name}`}>
                      <li>Variantes: {variantSummary(item)}</li>
                    </ul>
                  </div>
                  <div className="admin-item-actions" role="group" aria-label={`Acciones de ${item.name}`}>
                    <button
                      className="btn btn-secondary"
                      type="button"
                      onClick={(event) => {
                        editTriggerRef.current = event.currentTarget
                        loadProductForEdit(item)
                        write.reset()
                      }}
                    >
                      Editar
                    </button>
                    <button
                      aria-label={active ? `Desactivar producto ${item.name}` : `Reactivar producto ${item.name}`}
                      className={active ? 'btn btn-danger-soft' : 'btn btn-secondary'}
                      type="button"
                      onClick={() => void toggleProduct(item)}
                    >
                      {active ? 'Desactivar' : 'Reactivar'}
                    </button>
                  </div>
                </article>
              </li>
            )
          })}
        </ul>
        {!filteredProducts.length ? (
          <div className="admin-empty-card" role="status">
            <h3>No hay productos que coincidan con esos filtros.</h3>
            <p>Probá cambiar estado, categoría o stock para volver a ver productos.</p>
          </div>
        ) : null}
      </section>
      {editDraft && editingProduct ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section
            className="admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="product-edit-dialog-title"
            ref={editDialogRef}
            onKeyDown={handleEditDialogKeyDown}
          >
            <div className="admin-dialog-header">
              <div>
                <p className="admin-eyebrow">Edición rápida</p>
                <h3 id="product-edit-dialog-title">Editar producto {editingProduct.name}</h3>
                <p>Actualizá este producto sin volver al formulario superior.</p>
              </div>
              <button className="btn btn-secondary" type="button" onClick={requestCloseEdit} aria-label={`Cerrar edición de ${editingProduct.name}`} disabled={write.isPending}>
                Cerrar
              </button>
            </div>
            <form className="admin-form" onSubmit={(event) => void submitEdit(event)} noValidate>
              <section className="admin-card" aria-labelledby="product-edit-basics-title">
                <div className="admin-card-header">
                  <h3 id="product-edit-basics-title">Básicos del producto</h3>
                  <p>Datos visibles y categorización principal.</p>
                </div>
                <div className="admin-form-grid">
                  <label className="admin-field">
                    <span>Nombre</span>
                    <input
                      ref={editNameRef}
                      value={editDraft.name}
                      onChange={(event) => updateEditDraftName(event.target.value)}
                      aria-label="Nombre producto"
                    />
                  </label>
                  <label className="admin-field">
                    <span>Slug producto</span>
                    <input
                      value={editDraft.slug}
                      onChange={(event) => setEditDraftValue({ slugTouched: true, slug: slugify(event.target.value) })}
                      aria-label="Slug producto"
                    />
                  </label>
                  <label className="admin-field">
                    <span>Categoría producto</span>
                    <select value={editDraft.selectedCategoryId} onChange={(event) => setEditDraftValue({ selectedCategoryId: event.target.value })} aria-label="Categoría producto">
                      {categories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="admin-field admin-field-wide">
                    <span>Descripción producto</span>
                    <textarea value={editDraft.description} onChange={(event) => setEditDraftValue({ description: event.target.value })} aria-label="Descripción producto" />
                  </label>
                </div>
              </section>
              <fieldset className="admin-card admin-fieldset">
                <legend>Imagen principal</legend>
                <p className="admin-card-help">Subí una imagen JPG, PNG o WebP, o pegá una URL pública manualmente.</p>
                <div className="admin-form-grid">
                  <label className="admin-field">
                    <span>Subir imagen principal</span>
                    <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => void handleEditImageUpload(event.target.files?.[0])} aria-label="Subir imagen principal" />
                  </label>
                  <label className="admin-field">
                    <span>URL imagen principal</span>
                    <input
                      value={editDraft.imageUrl}
                      onChange={(event) => setEditDraftValue({ imageUrl: event.target.value, imagePreviewBroken: false })}
                      aria-label="URL imagen principal"
                    />
                  </label>
                  <label className="admin-field">
                    <span>Texto alternativo imagen</span>
                    <input value={editDraft.imageAltText} onChange={(event) => setEditDraftValue({ imageAltText: event.target.value })} aria-label="Texto alternativo imagen" />
                  </label>
                </div>
                {editDraft.imageUrl.trim() && isValidImageUrl(editDraft.imageUrl.trim()) ? (
                  <div className="admin-image-preview">
                    <p>Vista previa imagen principal</p>
                    <img
                      src={resolveImagePreviewSrc(editDraft.imageUrl.trim())}
                      alt={editDraft.imageAltText.trim() || editDraft.name.trim() || 'Vista previa imagen principal'}
                      onError={() => setEditDraftValue({ imagePreviewBroken: true })}
                    />
                    {editDraft.imagePreviewBroken ? <p>No se pudo cargar la vista previa de la imagen.</p> : null}
                  </div>
                ) : null}
              </fieldset>
              <fieldset className="admin-card admin-fieldset">
                <legend>Variantes</legend>
                <p className="admin-card-help">Separá precios, cantidades y stock por presentación.</p>
                {resolveProductType(editDraft.variants) === 'GRANEL' ? (
                  <div className="admin-card-help" aria-label="Stock granel compartido">
                    <p>El stock granel se administra una sola vez y las presentaciones comparten ese stock base.</p>
                    <div className="admin-form-grid">
                      <label className="admin-field">
                        <span>Stock total granel en gramos</span>
                        <input type="number" min="0" step="1" value={editDraft.stockBaseGrams} onChange={(event) => setEditDraftValue({ stockBaseGrams: event.target.value })} aria-label="Stock total granel en gramos" />
                      </label>
                      <label className="admin-field">
                        <span>Precio por kg granel</span>
                        <input type="number" min="0" step="0.01" value={editDraft.pricePerKg} onChange={(event) => setEditDraftValue({ pricePerKg: event.target.value })} aria-label="Precio por kg granel" />
                      </label>
                    </div>
                    <div className="admin-form-grid" aria-label="Precios calculados granel">
                      {GRANEL_FIXED_PRESENTATIONS.map((presentation, index) => (
                        <div className="admin-field" key={presentation.label} aria-label={`Precio calculado variante ${index + 1}`}>
                          <span>{presentation.label}</span>
                          <strong>{formatCurrency(calculateGranelPrice(Number(editDraft.pricePerKg), presentation.weightGrams))}</strong>
                          <small>Precio calculado desde precio por kg granel.</small>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : null}
                <div className="admin-preset-row" aria-label="Presets de variantes">
                  {VARIANT_PRESETS.map((preset) => (
                    <button className="btn btn-secondary admin-chip" key={preset.label} type="button" onClick={() => applyEditVariantPreset(preset)}>
                      Preset {preset.label}
                    </button>
                  ))}
                </div>
                {editDraft.variants.map((variant, index) => (
                  <div className="admin-variant-row" key={`${variant.id ?? 'new'}-${index}`}>
                    <h3>Variante {index + 1}</h3>
                    <div className="admin-form-grid admin-variant-grid">
                      <label className="admin-field">
                        <span>SKU variante {index + 1}</span>
                        <input value={variant.sku} onChange={(event) => updateEditVariant(index, { sku: event.target.value })} aria-label={`SKU variante ${index + 1}`} />
                      </label>
                      <label className="admin-field">
                        <span>Cantidad variante {index + 1}</span>
                        <input type="number" min="0" step="0.01" value={variant.amount} onChange={(event) => updateEditVariant(index, { amount: event.target.value })} aria-label={`Cantidad variante ${index + 1}`} />
                      </label>
                      <label className="admin-field">
                        <span>Unidad variante {index + 1}</span>
                        <select value={variant.unit} onChange={(event) => updateEditVariant(index, { unit: event.target.value as VariantUnit })} aria-label={`Unidad variante ${index + 1}`}>
                          <option value="g">g</option>
                          <option value="kg">kg</option>
                          <option value="ml">ml</option>
                          <option value="l">l</option>
                          <option value="unidad">unidad</option>
                        </select>
                      </label>
                      {resolveProductType(editDraft.variants) === 'GRANEL' ? (
                        <div className="admin-field" aria-label={`Precio calculado presentación variante ${index + 1}`}>
                          <span>Precio calculado variante {index + 1}</span>
                          <strong>{formatCurrency(calculateVariantPriceFromKg(Number(editDraft.pricePerKg), variant))}</strong>
                          <small>El stock de esta presentación sale del stock granel base.</small>
                        </div>
                      ) : (
                        <>
                          <label className="admin-field">
                            <span>Precio variante {index + 1}</span>
                            <input type="number" min="0" step="0.01" value={variant.price} onChange={(event) => updateEditVariant(index, { price: event.target.value })} aria-label={`Precio variante ${index + 1}`} />
                          </label>
                          <label className="admin-field">
                            <span>Stock variante {index + 1}</span>
                            <input type="number" min="0" step="1" value={variant.stock} onChange={(event) => updateEditVariant(index, { stock: event.target.value })} aria-label={`Stock variante ${index + 1}`} />
                          </label>
                        </>
                      )}
                      <label className="admin-field admin-checkbox-field">
                        <span>Activa variante {index + 1}</span>
                        <input type="checkbox" checked={variant.active} onChange={(event) => updateEditVariant(index, { active: event.target.checked })} aria-label={`Activa variante ${index + 1}`} />
                      </label>
                    </div>
                    <button className="btn btn-danger-soft" type="button" onClick={() => removeEditVariant(index)}>
                      Quitar variante {index + 1}
                    </button>
                  </div>
                ))}
                <button className="btn btn-secondary" type="button" onClick={addEditVariant}>
                  Agregar variante
                </button>
              </fieldset>
              <div className="admin-form-actions">
                <button className="btn btn-secondary" type="button" onClick={requestCloseEdit} disabled={write.isPending}>
                  Cancelar edición
                </button>
                <button className="btn btn-primary" type="submit" disabled={write.isPending}>
                  Guardar cambios
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </section>
  )

  function updateVariant(index: number, patch: Partial<ProductVariantFormRow>) {
    setVariants((current) => current.map((variant, variantIndex) => (variantIndex === index ? { ...variant, ...patch } : variant)))
  }

  function addVariant() {
    setVariants((current) => [...current, { ...DEFAULT_VARIANT }])
  }

  function applyVariantPreset(preset: { label: string; amount: string; unit: VariantUnit }) {
    const skuSeed = `${slugify(slug || name || 'producto').toUpperCase()}-${preset.label.toUpperCase()}`
    setVariants((current) => {
      const presetRow = { ...DEFAULT_VARIANT, amount: preset.amount, unit: preset.unit, sku: skuSeed }
      const matchingIndex = current.findIndex((variant) => variant.amount === preset.amount && variant.unit === preset.unit)
      if (matchingIndex >= 0) {
        return current.map((variant, index) => (index === matchingIndex ? { ...variant, amount: preset.amount, unit: preset.unit, sku: variant.sku || skuSeed } : variant))
      }
      const emptyIndex = current.findIndex(isBlankVariantPresetTarget)
      if (emptyIndex >= 0) return current.map((variant, index) => (index === emptyIndex ? { ...variant, ...presetRow } : variant))
      return [...current, presetRow]
    })
  }

  function removeVariant(index: number) {
    setVariants((current) => current.filter((_, variantIndex) => variantIndex !== index))
  }

  function resetForm(categoryId: string) {
    setName('')
    setSlug('')
    setSlugTouched(false)
    setDescription('')
    setImageUrl('')
    setImageAltText('')
    setImageId(undefined)
    setImagePreviewBroken(false)
    setVariants([{ ...DEFAULT_VARIANT }])
    setStockBaseGrams('')
    setPricePerKg('')
    setSelectedCategoryId(categoryId)
  }

  async function submitEdit(e: FormEvent) {
    e.preventDefault()
    if (write.isPending || !editingProduct || !editDraft) return
    const payload = buildProductWritePayload(editDraft, editingProduct.active ?? true)
    if (!payload.ok) {
      write.fail(payload.error)
      return
    }

    write.start()
    try {
      await updateAdminProduct(editingProduct.id, payload.value)
      const [products, availableCategories] = await Promise.all([listAdminProducts(), listAdminCategories()])
      setItems(products)
      setCategories(availableCategories)
      setSelectedCategoryId((current) => current || availableCategories[0]?.id || '')
      closeEdit()
      write.succeed('Producto guardado correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  function buildProductWritePayload(draft: ProductFormDraft, active: boolean) {
    if (!draft.name.trim()) {
      return { ok: false as const, error: { message: 'Nombre es requerido.', fieldErrors: [{ field: 'name', message: 'Nombre es requerido.' }] } }
    }
    if (!categories.length) return { ok: false as const, error: { message: 'Primero crea una categoría para poder guardar productos.' } }
    if (!draft.selectedCategoryId) return { ok: false as const, error: { message: 'No se pudo determinar la categoría del producto.' } }

    const normalizedName = draft.name.trim()
    const normalizedSlug = draft.slug.trim() || slugify(normalizedName)
    const normalizedDescription = draft.description.trim()
    const resolvedProductType = resolveProductType(draft.variants)
    const shouldGenerateGranelVariants = resolvedProductType === 'GRANEL' && Number(draft.pricePerKg) > 0
    const validationErrors = validateProductForm({
      description: normalizedDescription,
      imageUrl: draft.imageUrl.trim(),
      variants: draft.variants,
      skipVariantPrice: shouldGenerateGranelVariants,
    })
    if (validationErrors.length) return { ok: false as const, error: { message: 'Revisá los datos del producto.', fieldErrors: validationErrors } }

    const inventoryPolicy = resolveInventoryPolicy(resolvedProductType)
    const payloadVariants = shouldGenerateGranelVariants
      ? buildFixedGranelVariantPayloads(normalizedSlug, Number(draft.pricePerKg))
      : draft.variants.map((variant, index) => buildVariantPayload(variant, normalizedSlug, index))
    const normalizedImageUrl = draft.imageUrl.trim()
    return {
      ok: true as const,
      value: {
        name: normalizedName,
        slug: normalizedSlug,
        description: normalizedDescription,
        active,
        categoryId: draft.selectedCategoryId,
        productType: resolvedProductType,
        inventoryPolicy,
        stockBaseGrams: resolveStockBaseGrams(inventoryPolicy, payloadVariants, draft.stockBaseGrams),
        variants: payloadVariants,
        images: normalizedImageUrl
          ? [
              {
                id: draft.imageId,
                url: normalizedImageUrl,
                altText: draft.imageAltText.trim() || normalizedName,
                sortOrder: 0,
                primary: true,
              },
            ]
          : [],
      },
    }
  }

  function requestCloseEdit() {
    if (write.isPending) return
    closeEdit()
  }

  function closeEdit() {
    editTriggerRef.current?.focus()
    setEditingProduct(null)
    setEditDraft(null)
  }

  function handleEditDialogKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      requestCloseEdit()
      return
    }

    if (event.key !== 'Tab') return

    const focusableElements = getFocusableElements(editDialogRef.current)
    if (!focusableElements.length) return

    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]
    const activeElement = document.activeElement

    if (event.shiftKey && activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
      return
    }

    if (!event.shiftKey && activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }

  function setEditDraftValue(patch: Partial<ProductFormDraft>) {
    setEditDraft((current) => (current ? { ...current, ...patch } : current))
  }

  function updateEditDraftName(nextName: string) {
    setEditDraft((current) => {
      if (!current) return current
      return {
        ...current,
        name: nextName,
        slug: current.slugTouched ? current.slug : slugify(nextName),
      }
    })
  }

  function updateEditVariant(index: number, patch: Partial<ProductVariantFormRow>) {
    setEditDraft((current) =>
      current ? { ...current, variants: current.variants.map((variant, variantIndex) => (variantIndex === index ? { ...variant, ...patch } : variant)) } : current,
    )
  }

  function addEditVariant() {
    setEditDraft((current) => (current ? { ...current, variants: [...current.variants, { ...DEFAULT_VARIANT }] } : current))
  }

  function removeEditVariant(index: number) {
    setEditDraft((current) => (current ? { ...current, variants: current.variants.filter((_, variantIndex) => variantIndex !== index) } : current))
  }

  function applyEditVariantPreset(preset: { label: string; amount: string; unit: VariantUnit }) {
    setEditDraft((current) => {
      if (!current) return current
      const skuSeed = `${slugify(current.slug || current.name || 'producto').toUpperCase()}-${preset.label.toUpperCase()}`
      const presetRow = { ...DEFAULT_VARIANT, amount: preset.amount, unit: preset.unit, sku: skuSeed }
      const matchingIndex = current.variants.findIndex((variant) => variant.amount === preset.amount && variant.unit === preset.unit)
      if (matchingIndex >= 0) {
        return {
          ...current,
          variants: current.variants.map((variant, index) => (index === matchingIndex ? { ...variant, amount: preset.amount, unit: preset.unit, sku: variant.sku || skuSeed } : variant)),
        }
      }
      const emptyIndex = current.variants.findIndex(isBlankVariantPresetTarget)
      if (emptyIndex >= 0) return { ...current, variants: current.variants.map((variant, index) => (index === emptyIndex ? { ...variant, ...presetRow } : variant)) }
      return { ...current, variants: [...current.variants, presetRow] }
    })
  }

  async function handleEditImageUpload(file: File | undefined) {
    if (!file || write.isPending) return

    write.start()
    try {
      const result = await uploadAdminProductImage(file)
      setEditDraftValue({ imageUrl: result.url, imagePreviewBroken: false })
      write.succeed('Imagen subida correctamente.')
    } catch (error) {
      write.fail(mapAdminWriteError(error))
    }
  }

  function loadProductForEdit(item: AdminProductDto) {
    const primaryImage = item.images?.find((image) => image.primary) ?? item.images?.[0]
    setEditingProduct(item)
    setEditDraft({
      name: item.name,
      slug: item.slug?.trim() || slugify(item.name),
      slugTouched: true,
      description: item.description?.trim() || '',
      selectedCategoryId: item.categoryId || categories[0]?.id || '',
      imageId: primaryImage?.id,
      imageUrl: isPlaceholderProductImage(primaryImage?.url) ? '' : primaryImage?.url || '',
      imageAltText: primaryImage?.altText || item.name,
      imagePreviewBroken: false,
      variants: item.variants?.length ? item.variants.map(variantToFormRow) : [{ ...DEFAULT_VARIANT }],
      stockBaseGrams: item.stockBaseGrams == null ? '' : String(item.stockBaseGrams),
      pricePerKg: '',
    })
  }
}

function isProductActive(item: AdminProductDto): boolean {
  return item.active !== false && !item.deletedAt
}

function getFocusableElements(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  return Array.from(
    container.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => !element.hasAttribute('disabled') && element.getAttribute('aria-hidden') !== 'true')
}

function visibleProducts(
  items: AdminProductDto[],
  statusFilter: 'all' | 'active' | 'inactive',
  categoryFilter: string,
  stockFilter: StockFilter,
): AdminProductDto[] {
  return items.filter((item) => {
    if (categoryFilter !== 'all' && item.categoryId !== categoryFilter) return false
    if (statusFilter === 'active' && !isProductActive(item)) return false
    if (statusFilter === 'inactive' && isProductActive(item)) return false
    const stockState = getAdminStockState(item).state
    if (stockFilter === 'out') return stockState === 'no-stock'
    if (stockFilter === 'low') return stockState !== 'available'
    return true
  })
}

function resolveCategoryName(item: AdminProductDto, categories: AdminCategoryDto[]): string {
  return item.categoryName || categories.find((category) => category.id === item.categoryId)?.name || 'Sin categoría'
}

function primaryImageUrl(item: AdminProductDto): string {
  const imageUrl = item.images?.find((image) => image.primary)?.url || item.images?.[0]?.url
  return imageUrl && !isPlaceholderProductImage(imageUrl) ? imageUrl : 'Sin imagen'
}

function variantSummary(item: AdminProductDto): string {
  const stockState = getAdminStockState(item)
  if (item.inventoryPolicy === 'BULK_WEIGHT' || item.productType === 'GRANEL') {
    const variants = item.variants?.map((variant) => `${variant.unitLabel || `${variant.weightGrams}g`} · $${variant.price ?? 0}`).join(' | ') || 'Sin presentaciones'
    return `${stockState.summaryLabel} · ${variants}`
  }
  if (!item.variants?.length) return 'Sin variantes'
  return item.variants
    .map((variant) => {
      const label = variant.unitLabel || (variant.weightGrams ? `${variant.weightGrams}g` : 'unidad')
      const stock = variant.stockAvailable ?? 0
      const stockLabel = stockStatusLabelForVariant(stock)
      return `${label} · $${variant.price ?? 0} · stock ${stock}${stockLabel ? ` · ${stockLabel}` : ''}`
    })
    .join(' | ')
}

function validateProductForm(input: {
  description: string
  imageUrl: string
  variants: ProductVariantFormRow[]
  skipVariantPrice?: boolean
}): { field: string; message: string }[] {
  const errors: { field: string; message: string }[] = []
  if (!input.description) errors.push({ field: 'description', message: 'Descripción es requerida.' })
  if (input.imageUrl && !isValidImageUrl(input.imageUrl)) errors.push({ field: 'images', message: 'Ingresá una URL de imagen válida.' })
  if (!input.variants.length) {
    errors.push({ field: 'variants', message: 'Agregá al menos una variante.' })
    return errors
  }

  input.variants.forEach((variant) => {
    if (!input.skipVariantPrice && (Number(variant.price) <= 0 || Number.isNaN(Number(variant.price)))) {
      errors.push({ field: 'variants', message: 'El precio debe ser mayor a 0.' })
    }
    if (Number(variant.stock) < 0 || Number.isNaN(Number(variant.stock))) {
      errors.push({ field: 'variants', message: 'El stock no puede ser negativo.' })
    }
    if (variant.unit !== 'unidad' && (Number(variant.amount) <= 0 || Number.isNaN(Number(variant.amount)))) {
      errors.push({ field: 'variants', message: 'La cantidad debe ser mayor a 0.' })
    }
  })

  return errors
}

function buildFixedGranelVariantPayloads(slug: string, pricePerKg: number) {
  return GRANEL_FIXED_PRESENTATIONS.map((presentation) => ({
    id: undefined,
    sku: `${slug.toUpperCase()}-${presentation.label.toUpperCase()}`,
    unitType: 'WEIGHT' as const,
    weightGrams: presentation.weightGrams,
    unitLabel: presentation.label,
    price: calculateGranelPrice(pricePerKg, presentation.weightGrams),
    stockAvailable: 0,
    stockReserved: 0,
    active: true,
    attributesJson: null,
  }))
}

function calculateGranelPrice(pricePerKg: number, weightGrams: number): number {
  if (!Number.isFinite(pricePerKg) || pricePerKg <= 0) return 0
  return Math.round((pricePerKg * weightGrams) / 1000)
}

function calculateVariantPriceFromKg(pricePerKg: number, variant: ProductVariantFormRow): number {
  const amount = Number(variant.amount)
  if (!Number.isFinite(amount) || amount <= 0) return 0
  const weightGrams = toWeightGrams(amount, variant.unit) ?? 0
  return calculateGranelPrice(pricePerKg, weightGrams)
}

function formatCurrency(value: number): string {
  return `$${value}`
}

function buildVariantPayload(variant: ProductVariantFormRow, slug: string, index: number) {
  const amount = Number(variant.amount)
  const isWeight = variant.unit === 'g' || variant.unit === 'kg'
  return {
    id: variant.id,
    sku: variant.sku.trim() || `${slug.toUpperCase()}-${index + 1}`,
    unitType: isWeight ? ('WEIGHT' as const) : ('UNIT' as const),
    weightGrams: isWeight ? toWeightGrams(amount, variant.unit) : null,
    unitLabel: buildUnitLabel(variant),
    price: Number(variant.price),
    stockAvailable: Number(variant.stock),
    stockReserved: variant.stockReserved,
    active: variant.active,
    attributesJson: variant.attributesJson ?? null,
  }
}

function variantToFormRow(variant: NonNullable<AdminProductDto['variants']>[number]): ProductVariantFormRow {
  const parsed = parseVariantMeasurement(variant)
  return {
    id: variant.id,
    sku: variant.sku || '',
    amount: parsed.amount,
    unit: parsed.unit,
    price: String(variant.price ?? ''),
    stock: String(variant.stockAvailable ?? 0),
    stockReserved: variant.stockReserved ?? 0,
    active: variant.active ?? true,
    attributesJson: variant.attributesJson ?? null,
  }
}

function parseVariantMeasurement(variant: NonNullable<AdminProductDto['variants']>[number]): { amount: string; unit: VariantUnit } {
  const unitLabel = variant.unitLabel?.toLowerCase().trim() || ''
  const match = unitLabel.match(/^(\d+(?:\.\d+)?)(g|kg|ml|l)$/)
  if (match) return { amount: match[1], unit: match[2] as VariantUnit }
  if (variant.unitType === 'WEIGHT' && variant.weightGrams) return { amount: String(variant.weightGrams), unit: 'g' }
  return { amount: '1', unit: 'unidad' }
}

function buildUnitLabel(variant: ProductVariantFormRow): string {
  if (variant.unit === 'unidad') return 'unidad'
  return `${Number(variant.amount)}${variant.unit}`
}

function isValidImageUrl(value: string): boolean {
  if (value.startsWith('/') && !value.startsWith('//')) {
    return /^\/uploads\/product-images\/[^/?#]+\.(jpe?g|png|webp)$/i.test(value)
  }

  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

function isPlaceholderProductImage(value?: string | null): boolean {
  return value?.trim().toLowerCase() === '/placeholder-product.svg'
}

function resolveImagePreviewSrc(value: string): string {
  if (!value.startsWith('/') || value.startsWith('//')) return value

  const apiUrl = import.meta.env.VITE_API_URL?.trim()
  if (!apiUrl) return value

  try {
    return `${new URL(apiUrl, window.location.origin).origin}${value}`
  } catch {
    return value
  }
}

function isBlankVariantPresetTarget(variant: ProductVariantFormRow): boolean {
  return !variant.id && !variant.sku.trim() && !variant.price.trim() && variant.stock === DEFAULT_VARIANT.stock
}

function stockStatusLabelForVariant(stock: number): string {
  const stockState = getAdminStockState({ id: 'variant-summary', name: 'Variant summary', variants: [{ stockAvailable: stock }] })
  return stockState.state === 'available' ? '' : stockState.badgeLabel
}

function toWeightGrams(amount: number, unit: VariantUnit): number | null {
  if (unit === 'kg') return Math.round(amount * 1000)
  if (unit === 'g') return Math.round(amount)
  return null
}

function resolveProductType(variants: ProductVariantFormRow[]): ProductType {
  if (variants.some((variant) => variant.unit === 'ml' || variant.unit === 'l')) return 'ENVASADO'
  if (variants.some((variant) => variant.unit === 'g' || variant.unit === 'kg')) return 'GRANEL'
  return 'UNIDAD'
}

function resolveInventoryPolicy(productType: ProductType): InventoryPolicy {
  return productType === 'GRANEL' ? 'BULK_WEIGHT' : 'PER_VARIANT'
}

function resolveStockBaseGrams(inventoryPolicy: InventoryPolicy, variants: ReturnType<typeof buildVariantPayload>[], explicitStockBaseGrams = ''): number | null {
  if (inventoryPolicy !== 'BULK_WEIGHT') return 0

  const explicit = Number(explicitStockBaseGrams)
  if (explicitStockBaseGrams.trim() && Number.isFinite(explicit) && explicit >= 0) return Math.round(explicit)

  return variants.reduce((total, variant) => {
    const weightGrams = variant.weightGrams ?? 0
    return total + weightGrams * variant.stockAvailable
  }, 0)
}

function slugify(input: string): string {
  const normalized = input
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || 'producto'
}
