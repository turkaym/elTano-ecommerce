const API_URL = import.meta.env.VITE_API_URL?.trim() ?? ''

interface ApiErrorPayload {
  message?: string
}

export class ApiClientError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function joinUrl(base: string, path: string): string {
  if (!base) {
    return path
  }

  const normalizedBase = base.endsWith('/') ? base.slice(0, -1) : base
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(joinUrl(API_URL, path), {
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }

  return (await response.json()) as T
}

export async function postJson<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse> {
  const response = await fetch(joinUrl(API_URL, path), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    let errorMessage = `Request failed with status ${response.status}`
    try {
      const errorPayload = (await response.json()) as ApiErrorPayload
      if (errorPayload.message) {
        errorMessage = errorPayload.message
      }
    } catch {
      // ignore non-json errors and keep fallback message
    }

    throw new ApiClientError(response.status, errorMessage)
  }

  return (await response.json()) as TResponse
}
