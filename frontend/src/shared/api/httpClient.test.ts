import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError, deleteRequest, getJson, isUnauthorizedError, postJson } from './httpClient'

describe('httpClient unauthorized normalization', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
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

  it('injects X-XSRF-TOKEN only for non-GET admin requests', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token-123; path=/'
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 'prod-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await postJson('/api/admin/products', { name: 'Nuez' })

    const requestInit = fetchSpy.mock.calls[0]?.[1]
    expect(requestInit?.method).toBe('POST')
    expect((requestInit?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('csrf-token-123')
  })

  it('does not inject X-XSRF-TOKEN for GET and non-admin paths', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token-123; path=/'
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    await getJson('/api/admin/categories')
    await postJson('/api/public/contact', { message: 'hola' })

    const getHeaders = fetchSpy.mock.calls[0]?.[1]?.headers as Record<string, string>
    const publicPostHeaders = fetchSpy.mock.calls[1]?.[1]?.headers as Record<string, string>
    expect(getHeaders['X-XSRF-TOKEN']).toBeUndefined()
    expect(publicPostHeaders['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('propagates missing-CSRF write rejection from admin endpoints', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'FORBIDDEN',
          message: 'Missing CSRF token',
          correlationId: 'corr-csrf-403',
          fieldErrors: [],
        }),
        {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    const result = postJson('/api/admin/products', { name: 'Nuez' })

    await expect(result).rejects.toMatchObject({
      status: 403,
      code: 'FORBIDDEN',
      message: 'Missing CSRF token',
      correlationId: 'corr-csrf-403',
    })
    await expect(result).rejects.toSatisfy((error) => isUnauthorizedError(error))
  })
})
