import { vi } from 'vitest'
import { BASE_URL } from '../api/http'

export interface ApiCall {
  method: string
  path: string
  body: unknown
}

type Handler = (call: ApiCall) => unknown
/** A fixed `data` value, or a handler computing it (`unknown | Handler` would lose the handler's types). */
type Route = Handler | object | string | number | boolean | null

/**
 * Replaces `fetch` with a fake backend: `routes` maps `'GET /agents'` to the `data` of a success
 * envelope (or a function computing it). A handler that throws `{ code, message }` answers with
 * that error envelope. Unknown routes answer 404. Every request is recorded in `calls`.
 */
export function fakeApi(routes: Record<string, Route>) {
  const calls: ApiCall[] = []
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input))
    const path = url.pathname.slice(new URL(BASE_URL).pathname.length) + url.search
    const method = init?.method ?? 'GET'
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : init?.body
    const call = { method, path, body }
    calls.push(call)

    const route = routes[`${method} ${path}`]
    if (route === undefined) return envelope(404, 'No endpoint', null)
    try {
      const data = typeof route === 'function' ? await (route as Handler)(call) : route
      return envelope(200, 'success', data)
    } catch (error) {
      const { code, message } = error as { code: number; message: string }
      return envelope(code, message, null)
    }
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls }
}

function envelope(code: number, message: string, data: unknown) {
  return new Response(JSON.stringify({ code, message, data }), {
    headers: { 'Content-Type': 'application/json' },
  })
}

/** A promise you settle yourself, to hold a response back. */
export function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
