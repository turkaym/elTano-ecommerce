import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/httpClient'
import {
  createAdminProduct,
  deleteAdminProduct,
  getAdminImportJobStatus,
  listAdminOrders,
  listAdminProducts,
  restoreAdminProduct,
} from './adminOperationsService'

describe('admin operations service e2e-like flows', () => {
  afterEach(() => {
    vi.restoreAllMocks()
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
    expect(restored).toMatchObject({ id: 'prod-1', deletedAt: null })
  })

  it('retrieves orders list and import job status for operators', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ content: [{ id: 'ord-1', reference: 'ET-2026-0001' }] }), {
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

    expect(orders.content).toHaveLength(1)
    expect(orders.content[0].reference).toBe('ET-2026-0001')
    expect(jobStatus.status).toBe('COMPLETED')
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
})
