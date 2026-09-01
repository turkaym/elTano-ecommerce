import { beforeEach, describe, expect, it, vi } from 'vitest'
import { downloadAdminInventoryExport } from './adminOperationsService'

describe('admin inventory export service', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('downloads the authenticated XLSX and parses its UTF-8 content-disposition filename', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('xlsx', {
      status: 200,
      headers: { 'Content-Disposition': "attachment; filename*=UTF-8''inventario-completo-20260831-181500.xlsx" },
    }))

    const result = await downloadAdminInventoryExport()

    expect(fetchMock).toHaveBeenCalledWith(expect.stringMatching(/\/api\/admin\/inventory\/export\.xlsx$/), {
      credentials: 'include',
      headers: { Accept: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
    })
    expect(result.filename).toBe('inventario-completo-20260831-181500.xlsx')
    expect(result.blob).toBeInstanceOf(Blob)
    expect(result.blob.size).toBe(4)
  })
})
