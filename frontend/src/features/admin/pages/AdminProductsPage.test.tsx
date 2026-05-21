import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminProductsPage } from './AdminProductsPage'
import {
  createAdminProduct,
  deleteAdminProduct,
  listAdminCategories,
  listAdminProducts,
  mapAdminWriteError,
  restoreAdminProduct,
  uploadAdminProductImage,
  updateAdminProduct,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminProducts: vi.fn(async () => [
    {
      id: 'p-1',
      name: 'Nuez',
      slug: 'nuez',
      description: 'Nuez mariposa premium',
      categoryId: 'c-1',
      categoryName: 'Secos',
      productType: 'GRANEL',
      inventoryPolicy: 'BULK_WEIGHT',
      stockBaseGrams: 6000,
      stockReservedBaseGrams: 1500,
      active: true,
      variants: [
        {
          id: 'v-1',
          sku: 'NUEZ-250G',
          unitType: 'WEIGHT',
          weightGrams: 250,
          unitLabel: '250g',
          price: 2500,
          stockAvailable: 8,
          stockReserved: 1,
          active: true,
        },
      ],
      images: [{ id: 'img-1', url: 'https://cdn.test/nuez.jpg', altText: 'Nuez en bolsa', sortOrder: 0, primary: true }],
    },
    { id: 'p-2', name: 'Pera', slug: 'pera', categoryId: 'c-2', categoryName: 'Frutas', active: false, deletedAt: '2026-05-01T00:00:00Z' },
  ]),
  listAdminCategories: vi.fn(async () => [
    { id: 'c-1', name: 'Secos', slug: 'secos', active: true },
    { id: 'c-2', name: 'Frutas', slug: 'frutas', active: true },
  ]),
  createAdminProduct: vi.fn(async () => ({ id: 'p-2', name: 'Pera' })),
  updateAdminProduct: vi.fn(async () => ({ id: 'p-1', name: 'Nuez Premium' })),
  deleteAdminProduct: vi.fn(async () => undefined),
  restoreAdminProduct: vi.fn(async () => undefined),
  uploadAdminProductImage: vi.fn(async () => ({ url: '/uploads/product-images/uploaded-nuez.png' })),
  mapAdminWriteError: vi.fn(() => ({ message: 'Error API' })),
}))

describe('AdminProductsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.restoreAllMocks()
  })

  it('organizes the editor and catalog controls into accessible admin sections', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    expect(screen.getByRole('region', { name: /Básicos del producto/i })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: /Imagen principal/i })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: /Variantes/i })).toBeInTheDocument()

    const filters = screen.getByRole('region', { name: /Filtros de productos/i })
    expect(within(filters).getByLabelText(/Filtrar productos por estado/i)).toBeInTheDocument()
    expect(within(filters).getByLabelText(/Filtrar productos por categoría/i)).toBeInTheDocument()
    expect(within(filters).getByLabelText(/Filtrar productos por stock/i)).toBeInTheDocument()

    expect(screen.getByRole('article', { name: /Producto Nuez/i })).toHaveTextContent('Estado: Activo')
    expect(screen.getByRole('article', { name: /Producto Nuez/i })).toHaveTextContent('Stock bajo')
    expect(screen.getByRole('article', { name: /Producto Nuez/i })).toHaveTextContent('4,5 kg disponibles · 1,5 kg reservados')
    expect(screen.getByRole('button', { name: /Desactivar producto Nuez/i })).toBeInTheDocument()
  })

  it('shows validation error and blocks submit when name is empty', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Nombre es requerido.')
    expect(vi.mocked(createAdminProduct)).not.toHaveBeenCalled()
  })

  it('keeps the product form usable when the product list is empty', async () => {
    vi.mocked(listAdminProducts).mockResolvedValueOnce([])

    render(<AdminProductsPage />)

    expect(await screen.findByRole('button', { name: /Crear producto/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/Categoría producto/i)).toHaveValue('c-1')
    expect(screen.getByText('Sin productos')).toBeInTheDocument()
  })

  it('creates packaged product with explicit category, description, image and editable priced stock variant', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Durazno' } })
    fireEvent.change(screen.getByLabelText(/Slug producto/i), { target: { value: 'durazno-especial' } })
    fireEvent.change(screen.getByLabelText(/Categoría producto/i), { target: { value: 'c-2' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Durazno fresco de estación' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/durazno.jpg' } })
    fireEvent.change(screen.getByLabelText(/Texto alternativo imagen/i), { target: { value: 'Durazno amarillo' } })
    fireEvent.change(screen.getByLabelText(/SKU variante 1/i), { target: { value: 'DUR-500G' } })
    fireEvent.change(screen.getByLabelText(/Cantidad variante 1/i), { target: { value: '500' } })
    fireEvent.change(screen.getByLabelText(/Unidad variante 1/i), { target: { value: 'ml' } })
    fireEvent.change(screen.getByLabelText(/Precio variante 1/i), { target: { value: '3200' } })
    fireEvent.change(screen.getByLabelText(/Stock variante 1/i), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    await waitFor(() =>
      expect(vi.mocked(createAdminProduct)).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Durazno',
          slug: 'durazno-especial',
          categoryId: 'c-2',
          description: 'Durazno fresco de estación',
          active: true,
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          stockBaseGrams: 0,
          variants: [
            expect.objectContaining({
              sku: 'DUR-500G',
              unitType: 'UNIT',
              weightGrams: null,
              unitLabel: '500ml',
              price: 3200,
              stockAvailable: 12,
              stockReserved: 0,
              active: true,
            }),
          ],
          images: [
            expect.objectContaining({
              url: 'https://cdn.test/durazno.jpg',
              altText: 'Durazno amarillo',
              sortOrder: 0,
              primary: true,
            }),
          ],
        }),
      ),
    )
    expect(await screen.findByText('Producto guardado correctamente.')).toBeInTheDocument()
    expect(vi.mocked(listAdminProducts).mock.calls.length).toBeGreaterThan(1)
  })

  it('uses shared granel stock and price per kg to generate fixed calculated presentations without per-variant stock buckets', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Almendra' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Almendra tostada' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/almendra.jpg' } })
    fireEvent.change(screen.getByLabelText(/Stock total granel en gramos/i), { target: { value: '5000' } })
    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '3000' } })

    expect(screen.getByText(/El stock granel se administra una sola vez/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/Stock variante 1/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Precio variante 1/i)).not.toBeInTheDocument()
    expect(screen.getByLabelText(/Precio calculado variante 1/i)).toHaveTextContent('$300')
    expect(screen.getByLabelText(/Precio calculado variante 2/i)).toHaveTextContent('$750')
    expect(screen.getByLabelText(/Precio calculado variante 3/i)).toHaveTextContent('$1500')
    expect(screen.getByLabelText(/Precio calculado variante 4/i)).toHaveTextContent('$3000')

    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    await waitFor(() =>
      expect(vi.mocked(createAdminProduct)).toHaveBeenCalledWith(
        expect.objectContaining({
          productType: 'GRANEL',
          inventoryPolicy: 'BULK_WEIGHT',
          stockBaseGrams: 5000,
          variants: [
            expect.objectContaining({ unitLabel: '100g', weightGrams: 100, price: 300, stockAvailable: 0 }),
            expect.objectContaining({ unitLabel: '250g', weightGrams: 250, price: 750, stockAvailable: 0 }),
            expect.objectContaining({ unitLabel: '500g', weightGrams: 500, price: 1500, stockAvailable: 0 }),
            expect.objectContaining({ unitLabel: '1kg', weightGrams: 1000, price: 3000, stockAvailable: 0 }),
          ],
        }),
      ),
    )
  })

  it('recalculates granel calculated variant prices when price per kg changes', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '3000' } })
    expect(screen.getByLabelText(/Precio calculado variante 4/i)).toHaveTextContent('$3000')
    expect(screen.getByLabelText(/Precio calculado variante 3/i)).toHaveTextContent('$1500')

    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '4200' } })
    expect(screen.getByLabelText(/Precio calculado variante 4/i)).toHaveTextContent('$4200')
    expect(screen.getByLabelText(/Precio calculado variante 3/i)).toHaveTextContent('$2100')
    expect(screen.getByLabelText(/Precio calculado variante 2/i)).toHaveTextContent('$1050')
    expect(screen.getByLabelText(/Precio calculado variante 1/i)).toHaveTextContent('$420')
  })

  it('keeps stock and price inputs editable for envasado and unidad variants', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Unidad variante 1/i), { target: { value: 'ml' } })
    expect(screen.getByLabelText(/Precio variante 1/i)).toBeEnabled()
    expect(screen.getByLabelText(/Stock variante 1/i)).toBeEnabled()

    fireEvent.change(screen.getByLabelText(/Unidad variante 1/i), { target: { value: 'unidad' } })
    expect(screen.getByLabelText(/Precio variante 1/i)).toBeEnabled()
    expect(screen.getByLabelText(/Stock variante 1/i)).toBeEnabled()
  })

  it('keeps pending state and blocks duplicate submit until create resolves', async () => {
    let resolveCreate: ((value: { id: string; name: string }) => void) | undefined
    vi.mocked(createAdminProduct).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveCreate = resolve
        }),
    )

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Pera' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Pera orgánica' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/pera.jpg' } })
    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '1500' } })
    const submitButton = screen.getByRole('button', { name: /Crear producto/i })
    fireEvent.click(submitButton)
    fireEvent.click(submitButton)

    expect(await screen.findByRole('status')).toHaveTextContent('Guardando cambios…')
    expect(submitButton).toBeDisabled()
    expect(vi.mocked(createAdminProduct)).toHaveBeenCalledTimes(1)

    resolveCreate?.({ id: 'p-2', name: 'Pera' })

    await screen.findByText('Producto guardado correctamente.')
    await waitFor(() => expect(submitButton).not.toBeDisabled())
    expect(vi.mocked(listAdminProducts).mock.calls.length).toBeGreaterThan(1)
  })

  it('edits selected product loading existing image and variant values', async () => {
    vi.mocked(listAdminProducts)
      .mockResolvedValueOnce([
        {
          id: 'p-1',
          name: 'Nuez',
          slug: 'nuez',
          description: 'Nuez mariposa',
          categoryId: 'c-1',
          categoryName: 'Secos',
          active: true,
          variants: [
            {
              id: 'v-1',
              sku: 'NUEZ-250G',
              unitType: 'UNIT',
              weightGrams: null,
              unitLabel: '250ml',
              price: 2500,
              stockAvailable: 8,
              stockReserved: 1,
              active: true,
            },
          ],
          images: [{ id: 'img-1', url: 'https://cdn.test/nuez.jpg', altText: 'Nuez en bolsa', sortOrder: 0, primary: true }],
        },
      ])
      .mockResolvedValueOnce([{ id: 'p-1', name: 'Nuez Premium', slug: 'nuez', categoryId: 'c-2', categoryName: 'Frutas', active: true }])

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.click(screen.getByRole('button', { name: /Editar/i }))
    expect(screen.getByLabelText(/Descripción producto/i)).toHaveValue('Nuez mariposa')
    expect(screen.getByLabelText(/URL imagen principal/i)).toHaveValue('https://cdn.test/nuez.jpg')
    expect(screen.getByLabelText(/SKU variante 1/i)).toHaveValue('NUEZ-250G')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Nuez Premium' } })
    fireEvent.change(screen.getByLabelText(/Categoría producto/i), { target: { value: 'c-2' } })
    fireEvent.change(screen.getByLabelText(/Precio variante 1/i), { target: { value: '2750' } })
    fireEvent.click(screen.getByRole('button', { name: /Actualizar producto/i }))

    await waitFor(() =>
      expect(vi.mocked(updateAdminProduct)).toHaveBeenCalledWith(
        'p-1',
        expect.objectContaining({
          name: 'Nuez Premium',
          slug: 'nuez',
          categoryId: 'c-2',
          description: 'Nuez mariposa',
          variants: [expect.objectContaining({ id: 'v-1', sku: 'NUEZ-250G', price: 2750, stockAvailable: 8 })],
          images: [expect.objectContaining({ id: 'img-1', url: 'https://cdn.test/nuez.jpg', altText: 'Nuez en bolsa' })],
        }),
      ),
    )
    expect(await screen.findByText('Producto guardado correctamente.')).toBeInTheDocument()
    expect(await screen.findByText('Nuez Premium')).toBeInTheDocument()
    expect(screen.queryByText('Nuez')).not.toBeInTheDocument()
  })

  it('adds and removes variant rows before submit', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.click(screen.getByRole('button', { name: /Agregar variante/i }))
    expect(screen.getByLabelText(/SKU variante 2/i)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Aceite' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Aceite de oliva' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/aceite.jpg' } })
    fireEvent.change(screen.getByLabelText(/SKU variante 1/i), { target: { value: 'ACE-500ML' } })
    fireEvent.change(screen.getByLabelText(/Cantidad variante 1/i), { target: { value: '500' } })
    fireEvent.change(screen.getByLabelText(/Unidad variante 1/i), { target: { value: 'ml' } })
    fireEvent.change(screen.getByLabelText(/Precio variante 1/i), { target: { value: '9000' } })
    fireEvent.change(screen.getByLabelText(/Stock variante 1/i), { target: { value: '4' } })
    fireEvent.change(screen.getByLabelText(/SKU variante 2/i), { target: { value: 'ACE-1L' } })

    fireEvent.click(screen.getByRole('button', { name: /Quitar variante 2/i }))
    expect(screen.queryByLabelText(/SKU variante 2/i)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    await waitFor(() =>
      expect(vi.mocked(createAdminProduct)).toHaveBeenCalledWith(
        expect.objectContaining({
          productType: 'ENVASADO',
          inventoryPolicy: 'PER_VARIANT',
          variants: [expect.objectContaining({ sku: 'ACE-500ML', unitLabel: '500ml' })],
        }),
      ),
    )
  })

  it('adds variant presets with mapped amount, unit and SKU seed', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Aceite Oliva' } })
    fireEvent.click(screen.getByRole('button', { name: /Preset 500ml/i }))
    fireEvent.click(screen.getByRole('button', { name: /Preset 1kg/i }))

    expect(screen.getByLabelText(/Cantidad variante 1/i)).toHaveValue(500)
    expect(screen.getByLabelText(/Unidad variante 1/i)).toHaveValue('ml')
    expect(screen.getByLabelText(/SKU variante 1/i)).toHaveValue('ACEITE-OLIVA-500ML')
    expect(screen.getByLabelText(/Cantidad variante 2/i)).toHaveValue(1)
    expect(screen.getByLabelText(/Unidad variante 2/i)).toHaveValue('kg')
    expect(screen.getByLabelText(/SKU variante 2/i)).toHaveValue('ACEITE-OLIVA-1KG')
  })

  it('reuses an existing preset variant instead of creating duplicates', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Yerba' } })
    fireEvent.click(screen.getByRole('button', { name: /Preset 500ml/i }))
    fireEvent.change(screen.getByLabelText(/Precio variante 1/i), { target: { value: '2100' } })
    fireEvent.change(screen.getByLabelText(/Stock variante 1/i), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: /Preset 500ml/i }))

    expect(screen.getByLabelText(/SKU variante 1/i)).toHaveValue('YERBA-500ML')
    expect(screen.getByLabelText(/Precio variante 1/i)).toHaveValue(2100)
    expect(screen.getByLabelText(/Stock variante 1/i)).toHaveValue(7)
    expect(screen.queryByLabelText(/SKU variante 2/i)).not.toBeInTheDocument()
  })

  it('previews a valid image URL and shows fallback text when the preview fails', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/producto.jpg' } })
    fireEvent.change(screen.getByLabelText(/Texto alternativo imagen/i), { target: { value: 'Producto en frasco' } })

    const preview = screen.getByRole('img', { name: /Producto en frasco/i })
    expect(preview).toHaveAttribute('src', 'https://cdn.test/producto.jpg')

    fireEvent.error(preview)

    expect(screen.getByText('No se pudo cargar la vista previa de la imagen.')).toBeInTheDocument()
  })

  it('uploads a selected product image, stores the returned relative URL and previews it through the API origin', async () => {
    vi.stubEnv('VITE_API_URL', 'http://localhost:8080/api')
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    const file = new File(['image-bytes'], 'nuez premium.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText(/Subir imagen principal/i), { target: { files: [file] } })

    expect(await screen.findByText('Imagen subida correctamente.')).toBeInTheDocument()
    expect(vi.mocked(uploadAdminProductImage)).toHaveBeenCalledWith(file)
    expect(screen.getByLabelText(/URL imagen principal/i)).toHaveValue('/uploads/product-images/uploaded-nuez.png')
    expect(screen.getByRole('img', { name: /Vista previa imagen principal/i })).toHaveAttribute(
      'src',
      'http://localhost:8080/uploads/product-images/uploaded-nuez.png',
    )
  })

  it('submits the same relative product image URL returned by upload', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Almendra' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Almendra natural' } })
    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '4200' } })
    const file = new File(['image-bytes'], 'almendra.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText(/Subir imagen principal/i), { target: { files: [file] } })

    expect(await screen.findByText('Imagen subida correctamente.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    await waitFor(() =>
      expect(vi.mocked(createAdminProduct)).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Almendra',
          images: [expect.objectContaining({ url: '/uploads/product-images/uploaded-nuez.png' })],
        }),
      ),
    )
    expect(screen.queryByText('Imágenes: URL de imagen es requerida.')).not.toBeInTheDocument()
  })

  it('shows an upload error and keeps the manual image URL unchanged when upload fails', async () => {
    vi.mocked(uploadAdminProductImage).mockRejectedValueOnce(new Error('upload failed'))
    vi.mocked(mapAdminWriteError).mockReturnValueOnce({ message: 'No se pudo subir la imagen. Intentá nuevamente o pegá una URL.' })

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/manual.jpg' } })
    fireEvent.change(screen.getByLabelText(/Subir imagen principal/i), {
      target: { files: [new File(['not-image'], 'bad.txt', { type: 'text/plain' })] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo subir la imagen. Intentá nuevamente o pegá una URL.')
    expect(screen.getByLabelText(/URL imagen principal/i)).toHaveValue('https://cdn.test/manual.jpg')
  })

  it('shows the backend upload rejection reason when image upload fails with an API error', async () => {
    const apiError = new Error('Product image must be a JPG, PNG, or WebP file')
    vi.mocked(uploadAdminProductImage).mockRejectedValueOnce(apiError)
    vi.mocked(mapAdminWriteError).mockReturnValueOnce({
      message: 'Product image must be a JPG, PNG, or WebP file',
      code: 'BAD_REQUEST',
      correlationId: 'corr-upload-400',
    })

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Subir imagen principal/i), {
      target: { files: [new File(['not-image'], 'bad.txt', { type: 'text/plain' })] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent('Product image must be a JPG, PNG, or WebP file')
    expect(screen.getByRole('alert')).toHaveTextContent('Correlación: corr-upload-400')
    expect(vi.mocked(mapAdminWriteError)).toHaveBeenCalledWith(apiError)
  })

  it('validates image URL format before submit', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Arroz' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Arroz integral' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'not-a-url' } })
    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '1200' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    expect(await screen.findByText('Imágenes: Ingresá una URL de imagen válida.')).toBeInTheDocument()
    expect(vi.mocked(createAdminProduct)).not.toHaveBeenCalled()
  })

  it('validates image, variant count, price and stock before submit', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Arroz' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Arroz integral' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText(/Unidad variante 1/i), { target: { value: 'ml' } })
    fireEvent.change(screen.getByLabelText(/Precio variante 1/i), { target: { value: '0' } })
    fireEvent.change(screen.getByLabelText(/Stock variante 1/i), { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Imágenes: URL de imagen es requerida.')).toBeInTheDocument()
    expect(within(alert).getByText('Variantes: El precio debe ser mayor a 0.')).toBeInTheDocument()
    expect(within(alert).getByText('Variantes: El stock no puede ser negativo.')).toBeInTheDocument()
    expect(vi.mocked(createAdminProduct)).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /Quitar variante 1/i }))
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))
    expect(await screen.findByText('Variantes: Agregá al menos una variante.')).toBeInTheDocument()
  })

  it('shows error state when create API fails and allows retry reset', async () => {
    vi.mocked(createAdminProduct).mockRejectedValueOnce(new Error('boom'))
    vi.mocked(mapAdminWriteError).mockReturnValueOnce({
      message: 'No se pudo guardar producto.',
      fieldErrors: [{ field: 'name', message: 'El nombre debe ser único.' }],
    })

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    fireEvent.change(screen.getByLabelText(/Nombre producto/i), { target: { value: 'Pera' } })
    fireEvent.change(screen.getByLabelText(/Descripción producto/i), { target: { value: 'Pera orgánica' } })
    fireEvent.change(screen.getByLabelText(/URL imagen principal/i), { target: { value: 'https://cdn.test/pera.jpg' } })
    fireEvent.change(screen.getByLabelText(/Precio por kg granel/i), { target: { value: '1500' } })
    fireEvent.click(screen.getByRole('button', { name: /Crear producto/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo guardar producto.')
    expect(screen.getByText('Nombre: El nombre debe ser único.')).toBeInTheDocument()
    expect(vi.mocked(listAdminProducts)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(listAdminCategories)).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: /Reintentar/i }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('shows product status/category and filters by status and category', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Nuez')

    expect(screen.getByText('Categoría: Secos')).toBeInTheDocument()
    expect(screen.getByText('Estado: Activo')).toBeInTheDocument()
    expect(screen.getByText('Imagen: https://cdn.test/nuez.jpg')).toBeInTheDocument()
    expect(screen.getByText('Variantes: 4,5 kg disponibles · 1,5 kg reservados · 250g · $2500')).toBeInTheDocument()
    expect(screen.getByText('Estado: Inactivo')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar productos por estado/i), { target: { value: 'inactive' } })
    expect(screen.queryByText('Nuez')).not.toBeInTheDocument()
    expect(screen.getByText('Pera')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar productos por categoría/i), { target: { value: 'c-1' } })
    expect(screen.queryByText('Pera')).not.toBeInTheDocument()
  })

  it('shows low stock and out of stock labels and filters stock visibility', async () => {
    vi.mocked(listAdminProducts).mockResolvedValueOnce([
      {
        id: 'p-low',
        name: 'Aceite',
        slug: 'aceite',
        categoryId: 'c-1',
        categoryName: 'Secos',
        active: true,
        variants: [{ id: 'v-low', sku: 'ACE-500ML', unitType: 'UNIT', unitLabel: '500ml', price: 9000, stockAvailable: 5, active: true }],
      },
      {
        id: 'p-out',
        name: 'Harina',
        slug: 'harina',
        categoryId: 'c-1',
        categoryName: 'Secos',
        active: true,
        variants: [{ id: 'v-out', sku: 'HAR-1KG', unitType: 'WEIGHT', weightGrams: 1000, unitLabel: '1kg', price: 1100, stockAvailable: 0, active: true }],
      },
      {
        id: 'p-ok',
        name: 'Café',
        slug: 'cafe',
        categoryId: 'c-1',
        categoryName: 'Secos',
        active: true,
        variants: [{ id: 'v-ok', sku: 'CAF-250G', unitType: 'WEIGHT', weightGrams: 250, unitLabel: '250g', price: 4500, stockAvailable: 20, active: true }],
      },
    ])

    render(<AdminProductsPage />)
    await screen.findByText('Aceite')

    expect(screen.getByText('Variantes: 500ml · $9000 · stock 5 · Stock bajo')).toBeInTheDocument()
    expect(screen.getByText('Variantes: 1kg · $1100 · stock 0 · Sin stock')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar productos por stock/i), { target: { value: 'out' } })
    expect(screen.getByText('Harina')).toBeInTheDocument()
    expect(screen.queryByText('Aceite')).not.toBeInTheDocument()
    expect(screen.queryByText('Café')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar productos por stock/i), { target: { value: 'low' } })
    expect(screen.getByText('Aceite')).toBeInTheDocument()
    expect(screen.getByText('Harina')).toBeInTheDocument()
    expect(screen.queryByText('Café')).not.toBeInTheDocument()
  })

  it('uses shared GRANEL available/reserved stock semantics for badges and stock filters', async () => {
    vi.mocked(listAdminProducts).mockResolvedValueOnce([
      {
        id: 'p-granel-out',
        name: 'Pistachos',
        slug: 'pistachos',
        categoryId: 'c-1',
        categoryName: 'Secos',
        productType: 'GRANEL',
        inventoryPolicy: 'BULK_WEIGHT',
        stockBaseGrams: 5_000,
        stockReservedBaseGrams: 5_000,
        active: true,
        variants: [{ id: 'v-generated', unitLabel: '100g', price: 1200, stockAvailable: 12, active: true }],
      },
      {
        id: 'p-granel-low',
        name: 'Almendras',
        slug: 'almendras',
        categoryId: 'c-1',
        categoryName: 'Secos',
        productType: 'GRANEL',
        inventoryPolicy: 'BULK_WEIGHT',
        stockBaseGrams: 5_000,
        stockReservedBaseGrams: 500,
        active: true,
        variants: [{ id: 'v-generated-low', unitLabel: '250g', price: 2500, stockAvailable: 0, active: true }],
      },
      {
        id: 'p-granel-ok',
        name: 'Castañas',
        slug: 'castanas',
        categoryId: 'c-1',
        categoryName: 'Secos',
        productType: 'GRANEL',
        inventoryPolicy: 'BULK_WEIGHT',
        stockBaseGrams: 12_000,
        stockReservedBaseGrams: 0,
        active: true,
        variants: [{ id: 'v-generated-ok', unitLabel: '500g', price: 4200, stockAvailable: 0, active: true }],
      },
    ])

    render(<AdminProductsPage />)
    await screen.findByText('Pistachos')

    expect(screen.getByRole('article', { name: /Producto Pistachos/i })).toHaveTextContent('Sin stock')
    expect(screen.getByRole('article', { name: /Producto Pistachos/i })).toHaveTextContent('0 g disponibles · 5 kg reservados')
    expect(screen.getByRole('article', { name: /Producto Almendras/i })).toHaveTextContent('Stock bajo')
    expect(screen.getByRole('article', { name: /Producto Almendras/i })).toHaveTextContent('4,5 kg disponibles · 500 g reservados')
    expect(screen.getByRole('article', { name: /Producto Castañas/i })).toHaveTextContent('12 kg disponibles · 0 g reservados')

    fireEvent.change(screen.getByLabelText(/Filtrar productos por stock/i), { target: { value: 'out' } })
    expect(screen.getByText('Pistachos')).toBeInTheDocument()
    expect(screen.queryByText('Almendras')).not.toBeInTheDocument()
    expect(screen.queryByText('Castañas')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Filtrar productos por stock/i), { target: { value: 'low' } })
    expect(screen.getByText('Pistachos')).toBeInTheDocument()
    expect(screen.getByText('Almendras')).toBeInTheDocument()
    expect(screen.queryByText('Castañas')).not.toBeInTheDocument()
  })

  it('confirms and deactivates active products, then refreshes list', async () => {
    vi.mocked(listAdminProducts)
      .mockResolvedValueOnce([{ id: 'p-1', name: 'Nuez', slug: 'nuez', categoryId: 'c-1', categoryName: 'Secos', active: true }])
      .mockResolvedValueOnce([{ id: 'p-1', name: 'Nuez', slug: 'nuez', categoryId: 'c-1', categoryName: 'Secos', active: false, deletedAt: '2026-05-01T00:00:00Z' }])

    render(<AdminProductsPage />)
    await screen.findByText('Nuez')
    fireEvent.click(screen.getByRole('button', { name: /Desactivar producto Nuez/i }))

    await waitFor(() => expect(vi.mocked(deleteAdminProduct)).toHaveBeenCalledWith('p-1'))
    expect(await screen.findByText('Producto desactivado correctamente.')).toBeInTheDocument()
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Nuez'))
  })

  it('reactivates inactive products with confirmation', async () => {
    render(<AdminProductsPage />)
    await screen.findByText('Pera')
    fireEvent.click(screen.getByRole('button', { name: /Reactivar producto Pera/i }))

    await waitFor(() => expect(vi.mocked(restoreAdminProduct)).toHaveBeenCalledWith('p-2'))
    expect(await screen.findByText('Producto reactivado correctamente.')).toBeInTheDocument()
  })
})
