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

async function unwrap<T>(response: Response): Promise<T> {
  const body = (await response.json()) as ApiResponse<T>
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

function deleteJson<T>(path: string): Promise<T> {
  return fetch(buildUrl(path), { method: 'DELETE' }).then((res) => unwrap<T>(res))
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
