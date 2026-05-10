import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/httpClient'
import {
  awaitAdminImportTerminalStatus,
  createAdminImportJob,
  createAdminProduct,
  deleteAdminProduct,
  getAdminCatalogJobReport,
  getAdminCatalogJobRows,
  getAdminImportJobStatus,
  listAdminCatalogJobs,
  listAdminCategories,
  listAdminOrders,
  listAdminProducts,
  mapAdminWriteError,
  restoreAdminProduct,
  updateAdminCategory,
  updateAdminProduct,
  createAdminCategory,
  uploadAdminProductImage,
} from './adminOperationsService'

describe('admin operations service e2e-like flows', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('covers catalog create-delete-restore flow using admin endpoints', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'prod-1', name: 'Nuez' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'prod-1', deletedAt: null }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const created = await createAdminProduct({ name: 'Nuez' })
    await deleteAdminProduct('prod-1')
    const restored = await restoreAdminProduct('prod-1')

    expect(created).toMatchObject({ id: 'prod-1', name: 'Nuez' })
    expect(restored).toBeUndefined()
  })

  it('treats product restore 204 response as successful reactivation', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(restoreAdminProduct('prod-1')).resolves.toBeUndefined()

    const calls = vi.mocked(globalThis.fetch).mock.calls
    expect(String(calls[0][0])).toContain('/api/admin/products/prod-1/restore')
    expect(calls[0][1]?.method).toBe('POST')
  })

  it('uploads admin product image with FormData and returns the public URL payload', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-upload-token; path=/'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ url: '/uploads/product-images/nuez.png', contentType: 'image/png' }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const file = new File(['image-bytes'], 'nuez.png', { type: 'image/png' })
    const result = await uploadAdminProductImage(file)

    expect(result).toMatchObject({ url: '/uploads/product-images/nuez.png', contentType: 'image/png' })
    const [url, init] = vi.mocked(globalThis.fetch).mock.calls[0]
    expect(String(url)).toContain('/api/admin/uploads/product-images')
    expect(init?.method).toBe('POST')
    expect(init?.credentials).toBe('include')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-upload-token')
    expect((init?.headers as Record<string, string>)['Content-Type']).toBeUndefined()
  })

  it('propagates normalized admin upload errors with correlation metadata', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'BAD_REQUEST',
          message: 'Product image must be a JPG, PNG, or WebP file',
          correlationId: 'corr-upload-400',
          fieldErrors: [],
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const result = uploadAdminProductImage(new File(['text'], 'bad.txt', { type: 'text/plain' }))

    await expect(result).rejects.toBeInstanceOf(ApiClientError)
    await expect(result).rejects.toMatchObject({
      status: 400,
      code: 'BAD_REQUEST',
      correlationId: 'corr-upload-400',
      message: 'Product image must be a JPG, PNG, or WebP file',
    })
  })

  it('retrieves orders list and import job status for operators', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ items: [{ id: 'ord-1', reference: 'ET-2026-0001', status: 'PAID', customer: 'Ana', total: '$10', createdAt: '2026-01-01' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'COMPLETED' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const orders = await listAdminOrders()
    const jobStatus = await getAdminImportJobStatus('job-1')

    expect(orders.items).toHaveLength(1)
    expect(orders.items[0].reference).toBe('ET-2026-0001')
    expect(jobStatus.status).toBe('COMPLETED')
  })

  it('retrieves categories, jobs, rows and report DTOs for diagnostics', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'cat-1', name: 'Secos', slug: 'secos' }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'job-1', type: 'IMPORT', status: 'FAILED', createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:02:00Z' }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ rowNumber: 2, outcome: 'FAILED', errorCode: 'DUPLICATE_SLUG', errorMessage: 'slug duplicado', payload: '{"slug":"nuez"}' }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ summary: 'processed=2,succeeded=1,failed=1', failedRows: 1, rows: [{ rowNumber: 2, outcome: 'FAILED', errorCode: 'DUPLICATE_SLUG', errorMessage: 'slug duplicado', payload: '{"slug":"nuez"}' }] }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    const categories = await listAdminCategories()
    const jobs = await listAdminCatalogJobs()
    const rows = await getAdminCatalogJobRows('job-1')
    const report = await getAdminCatalogJobReport('job-1')

    expect(categories[0].slug).toBe('secos')
    expect(jobs[0]).toMatchObject({
      id: 'job-1',
      type: 'IMPORT',
      status: 'FAILED',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:02:00Z',
    })
    expect(rows[0].outcome).toBe('FAILED')
    expect(rows[0].errorMessage).toContain('duplicado')
    expect(report.rows[0].errorCode).toBe('DUPLICATE_SLUG')

    const fetchSpy = vi.mocked(globalThis.fetch)
    const requestedDiagnostics = fetchSpy.mock.calls.some(([input, init]) => {
      const url = typeof input === 'string' ? input : input instanceof Request ? input.url : ''
      const method = init?.method ?? (input instanceof Request ? input.method : undefined) ?? 'GET'
      return method === 'GET' && url.includes('/api/admin/catalog/jobs/job-1/report/diagnostics')
    })

    expect(requestedDiagnostics).toBe(true)
  })

  it('treats import creation as non-terminal enqueue acknowledgement', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'job-queued-1', status: 'QUEUED' }), {
        status: 202,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const enqueue = await createAdminImportJob('categorySlug,name,slug,description\nfrutos-secos,Almendra,almendra,ok')

    expect(enqueue.id).toBe('job-queued-1')
    expect(enqueue.status).toBe('QUEUED')
  })

  it('surfaces enqueue failure status when csv import endpoint rejects request', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('bad request', { status: 400 }))

    await expect(createAdminImportJob('invalid-csv')).rejects.toThrow(
      'Import enqueue failed with status 400',
    )
  })

  it('polls until terminal completed state and returns final status payload', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'QUEUED' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'PROCESSING' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'COMPLETED', summary: 'processed=2,succeeded=2,failed=0' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const finalStatus = await awaitAdminImportTerminalStatus('job-1', { maxAttempts: 3, delayMs: 0 })

    expect(finalStatus.status).toBe('COMPLETED')
    expect(finalStatus.summary).toContain('processed=2')
  })

  it('emits polling progress updates for non-terminal and terminal statuses', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'QUEUED' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ id: 'job-1', status: 'COMPLETED' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const onProgress = vi.fn()
    const finalStatus = await awaitAdminImportTerminalStatus('job-1', {
      maxAttempts: 2,
      delayMs: 0,
      onProgress,
    })

    expect(finalStatus.status).toBe('COMPLETED')
    expect(onProgress).toHaveBeenCalledTimes(2)
    expect(onProgress.mock.calls[0][0]).toMatchObject({ status: 'QUEUED' })
    expect(onProgress.mock.calls[1][0]).toMatchObject({ status: 'COMPLETED' })
  })

  it('returns queued fallback when polling attempts are exhausted before first request', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const finalStatus = await awaitAdminImportTerminalStatus('job-timeout', { maxAttempts: 0, delayMs: 0 })

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(finalStatus).toMatchObject({ id: 'job-timeout', status: 'QUEUED' })
  })

  it('returns terminal failed payload with failure details for operators', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          id: 'job-failed-1',
          status: 'FAILED',
          summary: null,
          lastError: 'Import payload missing for job job-failed-1',
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const finalStatus = await awaitAdminImportTerminalStatus('job-failed-1', { maxAttempts: 1, delayMs: 0 })

    expect(finalStatus.status).toBe('FAILED')
    expect(finalStatus.lastError).toContain('Import payload missing')
  })

  it('keeps guard-adjacent product list request available for admin shell bootstrap', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify([{ id: 'prod-1', name: 'Nuez' }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const products = await listAdminProducts()

    expect(products).toHaveLength(1)
    expect(products[0].name).toBe('Nuez')
  })

  it('propagates normalized correlationId-aware errors for failed delete operations', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'CONFLICT',
          message: 'Cannot delete while import is processing',
          correlationId: 'corr-delete-409',
          fieldErrors: [],
        }),
        {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const result = deleteAdminProduct('prod-1')

    await expect(result).rejects.toBeInstanceOf(ApiClientError)
    await expect(result).rejects.toMatchObject({
      status: 409,
      code: 'CONFLICT',
      correlationId: 'corr-delete-409',
      message: 'Cannot delete while import is processing',
    })
  })

  it('sends product/category update wrappers to expected paths and methods', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'prod-1', name: 'Nuevo' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'cat-1', name: 'Secos' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'cat-2', name: 'Frutas' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await updateAdminProduct('prod-1', { name: 'Nuevo' })
    await updateAdminCategory('cat-1', { name: 'Secos' })
    await createAdminCategory({ name: 'Frutas' })

    const calls = vi.mocked(globalThis.fetch).mock.calls
    const updateProductUrl = String(calls[0][0])
    const updateCategoryUrl = String(calls[1][0])
    expect(updateProductUrl).toContain('/api/admin/products/prod-1')
    expect(calls[0][1]?.method).toBe('PUT')
    expect(updateCategoryUrl).toContain('/api/admin/categories/cat-1')
    expect(calls[1][1]?.method).toBe('PUT')
    expect(String(calls[2][0])).toContain('/api/admin/categories')
    expect(calls[2][1]?.method).toBe('POST')
  })

  it('maps put wrapper errors even when response body is not json', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response('service unavailable', {
        status: 503,
        headers: { 'Content-Type': 'text/plain' },
      }),
    )

    const result = updateAdminProduct('prod-1', { name: 'Nuevo' })

    await expect(result).rejects.toBeInstanceOf(ApiClientError)
    await expect(result).rejects.toMatchObject({
      status: 503,
      message: 'Request failed with status 503',
      fieldErrors: [],
    })
  })

  it('maps 4xx and 5xx write errors with correlation info', () => {
    const badRequest = new ApiClientError(400, 'Nombre requerido', {
      code: 'VALIDATION_ERROR',
      correlationId: 'corr-400',
      fieldErrors: [{ field: 'name', message: 'requerido' }],
    })
    const serverError = new ApiClientError(500, 'boom', {
      code: 'INTERNAL',
      correlationId: 'corr-500',
    })

    expect(mapAdminWriteError(badRequest)).toMatchObject({
      message: 'Nombre requerido',
      code: 'VALIDATION_ERROR',
      correlationId: 'corr-400',
    })
    expect(mapAdminWriteError(serverError)).toMatchObject({
      message: expect.stringContaining('Error interno del servidor'),
      code: 'INTERNAL',
      correlationId: 'corr-500',
    })
  })
})
