export const BASE_URL = 'http://localhost:8080/api'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

type QueryParams = Record<string, string | number | boolean | undefined>

function buildUrl(path: string, params?: QueryParams): string {
  const url = new URL(`${BASE_URL}${path}`)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) url.searchParams.set(key, String(value))
    }
  }
  return url.toString()
}

/**
 * Builds a path whose interpolated values are encoded, so an id cannot add path segments or a query
 * (`path\`/agents/${id}\``). "." and ".." are refused: URL parsing would resolve them even encoded.
 */
export function path(strings: TemplateStringsArray, ...values: string[]): string {
  return strings.reduce((result, part, i) => {
    if (i === 0) return part
    const value = values[i - 1]
    if (value === '.' || value === '..') throw new Error(`Invalid path value: ${value}`)
    return result + encodeURIComponent(value) + part
  }, '')
}

/** Anything that is not the backend's JSON envelope (a proxy page, an empty body) keeps its HTTP status. */
async function unwrap<T>(response: Response): Promise<T> {
  const isJson = response.headers.get('Content-Type')?.includes('application/json') ?? false
  const body = isJson ? ((await response.json().catch(() => null)) as ApiResponse<T> | null) : null
  if (body === null || typeof body !== 'object') {
    throw new ApiError(
      response.status,
      response.statusText || 'Unexpected response from the server',
    )
  }
  if (!response.ok || body.code !== 200) {
    throw new ApiError(body.code ?? response.status, body.message ?? response.statusText)
  }
  return body.data
}

function getJson<T>(path: string, params?: QueryParams): Promise<T> {
  return fetch(buildUrl(path, params), { method: 'GET' }).then((res) => unwrap<T>(res))
}

function sendJson<T>(method: 'POST' | 'PATCH' | 'PUT', path: string, body?: unknown): Promise<T> {
  return fetch(buildUrl(path), {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).then((res) => unwrap<T>(res))
}

/** `keepalive` lets the request finish after the page is closed (sent from `pagehide`). */
function deleteJson<T>(path: string, options?: { keepalive?: boolean }): Promise<T> {
  return fetch(buildUrl(path), { method: 'DELETE', keepalive: options?.keepalive }).then((res) =>
    unwrap<T>(res),
  )
}

function postForm<T>(path: string, formData: FormData): Promise<T> {
  return fetch(buildUrl(path), { method: 'POST', body: formData }).then((res) => unwrap<T>(res))
}

export const http = {
  get: getJson,
  post: <T>(path: string, body?: unknown) => sendJson<T>('POST', path, body),
  patch: <T>(path: string, body?: unknown) => sendJson<T>('PATCH', path, body),
  delete: deleteJson,
  postForm,
}
