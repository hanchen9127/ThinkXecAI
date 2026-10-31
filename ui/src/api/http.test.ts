import { describe, expect, it, vi } from 'vitest'
import { ApiError, BASE_URL, http, path } from './http'

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function json(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: { 'Content-Type': 'application/json' },
  })
}

async function rejection(promise: Promise<unknown>): Promise<ApiError> {
  const error = await promise.then(
    () => {
      throw new Error('expected the request to fail')
    },
    (e: unknown) => e,
  )
  expect(error).toBeInstanceOf(ApiError)
  return error as ApiError
}

describe('path', () => {
  it('encodes interpolated values so they cannot add segments or a query', () => {
    expect(path`/agents/${'a/b?c=1#d'}`).toBe('/agents/a%2Fb%3Fc%3D1%23d')
  })

  it('keeps the literal parts and encodes every value', () => {
    expect(path`/documents/kb/${'k 1'}/x/${'%'}`).toBe('/documents/kb/k%201/x/%25')
  })

  it.each(['.', '..'])('refuses %j, which URL parsing would resolve even when encoded', (value) => {
    expect(() => path`/agents/${value}`).toThrow(`Invalid path value: ${value}`)
  })

  it('allows dots inside a value', () => {
    expect(path`/agents/${'a..b'}`).toBe('/agents/a..b')
  })
})

describe('http', () => {
  it('returns data from a success envelope', async () => {
    stubFetch(json({ code: 200, message: 'success', data: [{ id: '1' }] }))

    await expect(http.get('/agents')).resolves.toEqual([{ id: '1' }])
  })

  it('returns null data from a success envelope without data', async () => {
    stubFetch(json({ code: 200, message: 'success', data: null }))

    await expect(http.delete('/agents/1')).resolves.toBeNull()
  })

  it('throws the envelope code and message of an error with HTTP 200', async () => {
    stubFetch(json({ code: 404, message: 'Agent not found', data: null }))

    const error = await rejection(http.get('/agents/1'))
    expect(error.code).toBe(404)
    expect(error.message).toBe('Agent not found')
  })

  it('throws the envelope of a real HTTP error status', async () => {
    stubFetch(json({ code: 403, message: 'Host not allowed', data: null }, { status: 403 }))

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(403)
    expect(error.message).toBe('Host not allowed')
  })

  it('keeps the HTTP status of a reply that is not JSON', async () => {
    stubFetch(
      new Response('<html>Bad Gateway</html>', {
        status: 502,
        statusText: 'Bad Gateway',
        headers: { 'Content-Type': 'text/html' },
      }),
    )

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(502)
    expect(error.message).toBe('Bad Gateway')
  })

  it('does not trust an envelope sent without the JSON content type', async () => {
    stubFetch(
      new Response(JSON.stringify({ code: 200, message: 'success', data: 'x' }), {
        status: 200,
        statusText: 'OK',
        headers: { 'Content-Type': 'text/plain' },
      }),
    )

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(200)
    expect(error.message).toBe('OK')
  })

  it('fails on an HTTP error status even when the envelope says success', async () => {
    stubFetch(json({ code: 200, message: 'success', data: 'x' }, { status: 502 }))

    await rejection(http.get('/agents'))
  })

  it('keeps the HTTP status of an empty JSON reply', async () => {
    stubFetch(
      new Response('', {
        status: 500,
        statusText: 'Internal Server Error',
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(500)
    expect(error.message).toBe('Internal Server Error')
  })

  it('keeps the HTTP status of a JSON null reply', async () => {
    stubFetch(json(null, { status: 503, statusText: 'Service Unavailable' }))

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(503)
  })

  it('names the problem when a reply has neither a body nor a status text', async () => {
    stubFetch(new Response(null, { status: 200 }))

    const error = await rejection(http.get('/agents'))
    expect(error.code).toBe(200)
    expect(error.message).toBe('Unexpected response from the server')
  })

  it('sends query parameters and drops undefined ones', async () => {
    const fetchMock = stubFetch(json({ code: 200, message: 'success', data: [] }))

    await http.get('/agents', { a: 'x y', b: 2, c: false, d: undefined })

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/agents?a=x+y&b=2&c=false`, {
      method: 'GET',
    })
  })

  it('sends a JSON body with its content type', async () => {
    const fetchMock = stubFetch(json({ code: 200, message: 'success', data: { id: '1' } }))

    await http.patch('/agents/1', { name: 'A' })

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/agents/1`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: '{"name":"A"}',
    })
  })

  it('sends a form without a content type, so the browser adds the multipart boundary', async () => {
    const fetchMock = stubFetch(json({ code: 200, message: 'success', data: { id: '1' } }))
    const form = new FormData()
    form.append('kbId', 'k')

    await http.postForm('/documents/upload', form)

    expect(fetchMock).toHaveBeenCalledWith(`${BASE_URL}/documents/upload`, {
      method: 'POST',
      body: form,
    })
  })
})
