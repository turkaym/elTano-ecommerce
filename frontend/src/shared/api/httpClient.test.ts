import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError, deleteRequest, getJson, isUnauthorizedError, postJson } from './httpClient'

describe('httpClient unauthorized normalization', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('throws ApiClientError for GET failures and flags unauthorized statuses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'UNAUTHORIZED',
          message: 'Credenciales vencidas',
          correlationId: 'corr-401',
          fieldErrors: [],
        }),
        {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const result = getJson('/api/admin/categories')

    await expect(result).rejects.toBeInstanceOf(ApiClientError)
    await expect(result).rejects.toMatchObject({
      status: 401,
      message: 'Credenciales vencidas',
      code: 'UNAUTHORIZED',
      correlationId: 'corr-401',
      fieldErrors: [],
    })

    await expect(result).rejects.toSatisfy((error) => isUnauthorizedError(error))
  })

  it('treats 403 responses as unauthorized for guard handling', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'Sin permisos' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = postJson('/api/admin/products', { name: 'Nuez' })

    await expect(result).rejects.toSatisfy((error) => isUnauthorizedError(error))
  })

  it('normalizes correlationId-aware delete errors for admin operations', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'UNPROCESSABLE_ENTITY',
          message: 'Product images validation failed',
          correlationId: 'corr-422',
          fieldErrors: [{ field: 'images[0].url', message: 'invalid' }],
        }),
        {
          status: 422,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const result = deleteRequest('/api/admin/products/prod-1')

    await expect(result).rejects.toMatchObject({
      status: 422,
      code: 'UNPROCESSABLE_ENTITY',
      correlationId: 'corr-422',
      fieldErrors: [{ field: 'images[0].url', message: 'invalid' }],
    })
  })
})
