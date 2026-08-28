import { afterEach, describe, expect, it, vi } from 'vitest'
import { bootstrapAdminCsrf, getAdminSession, loginAdmin, logoutAdmin } from './adminAuthApi'

describe('admin auth API', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('bootstraps CSRF and submits form credentials without Authorization', async () => {
    document.cookie = 'XSRF-TOKEN=login-csrf; path=/'
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await loginAdmin('admin@example.test', 'secret password')

    expect(String(fetchSpy.mock.calls[0][0])).toMatch(/\/api\/admin\/auth\/csrf$/)
    expect(fetchSpy.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include' }))
    const [loginUrl, loginInit] = fetchSpy.mock.calls[1]
    expect(String(loginUrl)).toMatch(/\/api\/admin\/auth\/login$/)
    expect(loginInit?.method).toBe('POST')
    expect(loginInit?.credentials).toBe('include')
    expect(loginInit?.body).toBeInstanceOf(URLSearchParams)
    expect(String(loginInit?.body)).toContain('username=admin%40example.test')
    expect(String(loginInit?.body)).toContain('password=secret+password')
    expect((loginInit?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('login-csrf')
    expect((loginInit?.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it('checks the authenticated session with cookies and no Authorization header', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ authenticated: true, username: 'admin-user', roles: ['ADMIN'] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(getAdminSession()).resolves.toMatchObject({ authenticated: true, roles: ['ADMIN'] })
    expect(fetchSpy.mock.calls[0][1]).toMatchObject({ credentials: 'include' })
    expect((fetchSpy.mock.calls[0][1]?.headers as Record<string, string>).Authorization).toBeUndefined()
  })

  it('resolves logout after a 204 response', async () => {
    document.cookie = 'XSRF-TOKEN=logout-csrf; path=/'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(logoutAdmin()).resolves.toBeUndefined()
  })

  it('treats an expired session as a completed logout', async () => {
    document.cookie = 'XSRF-TOKEN=logout-csrf; path=/'
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(logoutAdmin()).resolves.toBeUndefined()
    expect(fetchSpy.mock.calls[0][1]?.method).toBe('POST')
    expect((fetchSpy.mock.calls[0][1]?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('logout-csrf')
  })

  it('rejects logout after a non-401 failure', async () => {
    document.cookie = 'XSRF-TOKEN=logout-csrf; path=/'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'CSRF rejected' }), { status: 403 }),
    )

    await expect(logoutAdmin()).rejects.toMatchObject({ status: 403, message: 'CSRF rejected' })
  })

  it('exposes standalone CSRF bootstrap', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(null, { status: 204 }))
    await bootstrapAdminCsrf()
    expect(fetchSpy).toHaveBeenCalledTimes(1)
  })
})
