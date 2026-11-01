import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import ChatSessionsProvider from '../contexts/ChatSessionsProvider'
import { deferred, fakeApi } from '../test/fakeApi'
import { useAgents, useAgentsState } from './useAgents'
import { useChatSessions } from './useChatSessions'
import { useDocuments } from './useDocuments'
import { useKnowledgeBases, useKnowledgeBasesState } from './useKnowledgeBases'
import { useOptionalTools } from './useOptionalTools'

function count(calls: { method: string; path: string }[], method: string, path: string) {
  return calls.filter((c) => c.method === method && c.path === path).length
}

describe('useAgents', () => {
  it('loads the agents', async () => {
    fakeApi({ 'GET /agents': [{ id: 'a1', name: 'Store analyst' }] })
    const { result } = renderHook(() => useAgentsState())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.agents).toEqual([{ id: 'a1', name: 'Store analyst' }])
  })

  it('creates an agent, returns it, and reloads the list', async () => {
    let agents = [{ id: 'a1', name: 'Store analyst' }]
    const api = fakeApi({
      'GET /agents': () => agents,
      'POST /agents': ({ body }) => {
        agents = [{ id: 'a2', ...(body as { name: string }) }, ...agents]
        return agents[0]
      },
    })
    const { result } = renderHook(() => useAgentsState())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let created: unknown
    await act(async () => {
      created = await result.current.create({ name: 'Trip planner', model: 'deepseek-chat' })
    })

    expect(created).toEqual({ id: 'a2', name: 'Trip planner', model: 'deepseek-chat' })
    await waitFor(() => expect(result.current.agents).toHaveLength(2))
    expect(count(api.calls, 'GET', '/agents')).toBe(2)
  })

  it('sends updates and deletes to the agent path, then reloads', async () => {
    const api = fakeApi({
      'GET /agents': [],
      'PATCH /agents/a1': { id: 'a1', name: 'Renamed' },
      'DELETE /agents/a1': null,
    })
    const { result } = renderHook(() => useAgentsState())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(() => result.current.update('a1', { name: 'Renamed' }))
    await act(() => result.current.remove('a1'))

    expect(api.calls.find((c) => c.method === 'PATCH')?.body).toEqual({ name: 'Renamed' })
    await waitFor(() => expect(count(api.calls, 'GET', '/agents')).toBe(3))
  })

  it('rejects with the backend message and does not reload when a change fails', async () => {
    const api = fakeApi({
      'GET /agents': [],
      'POST /agents': () => {
        throw { code: 400, message: 'name: must not be blank' }
      },
    })
    const { result } = renderHook(() => useAgentsState())
    await waitFor(() => expect(result.current.loading).toBe(false))

    const failure = result.current.create({ name: ' ', model: 'deepseek-chat' })

    await expect(failure).rejects.toEqual(new ApiError(400, 'name: must not be blank'))
    expect(count(api.calls, 'GET', '/agents')).toBe(1)
  })

  it('reports a failed load', async () => {
    fakeApi({})
    const { result } = renderHook(() => useAgentsState())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBeInstanceOf(ApiError)
    expect(result.current.agents).toEqual([])
  })
})

describe('useKnowledgeBases', () => {
  it('loads, creates, renames, and deletes, reloading after each change', async () => {
    const api = fakeApi({
      'GET /knowledge-bases': [{ id: 'k1', name: 'Policies' }],
      'POST /knowledge-bases': { id: 'k2', name: 'Products' },
      'PATCH /knowledge-bases/k1': { id: 'k1', name: 'Store policies' },
      'DELETE /knowledge-bases/k1': null,
    })
    const { result } = renderHook(() => useKnowledgeBasesState())
    await waitFor(() => expect(result.current.knowledgeBases).toHaveLength(1))

    await act(() => result.current.create({ name: 'Products' }))
    await act(() => result.current.update('k1', { name: 'Store policies' }))
    await act(() => result.current.remove('k1'))

    await waitFor(() => expect(count(api.calls, 'GET', '/knowledge-bases')).toBe(4))
  })
})

describe('useDocuments', () => {
  it('loads nothing without a knowledge base', () => {
    const api = fakeApi({})
    const { result } = renderHook(() => useDocuments(undefined))

    expect(result.current).toMatchObject({ documents: [], loading: false })
    expect(api.calls).toEqual([])
  })

  it('loads the documents of the knowledge base, with its id encoded', async () => {
    const api = fakeApi({ 'GET /documents/kb/k%201': [{ id: 'd1', name: 'returns.md' }] })
    const { result } = renderHook(() => useDocuments('k 1'))

    await waitFor(() => expect(result.current.documents).toHaveLength(1))
    expect(api.calls[0].path).toBe('/documents/kb/k%201')
  })

  it('uploads a file to its knowledge base and reloads the list', async () => {
    const api = fakeApi({
      'GET /documents/kb/k1': [],
      'POST /documents/upload': { id: 'd1', name: 'returns.md' },
    })
    const { result } = renderHook(() => useDocuments('k1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    const file = new File(['# Returns'], 'returns.md', { type: 'text/markdown' })

    await act(() => result.current.upload(file))

    const form = api.calls.find((c) => c.method === 'POST')?.body as FormData
    expect(form.get('kbId')).toBe('k1')
    expect((form.get('file') as File).name).toBe('returns.md')
    await waitFor(() => expect(count(api.calls, 'GET', '/documents/kb/k1')).toBe(2))
  })

  it('never shows the previous knowledge base after switching', async () => {
    const slow = deferred<unknown>()
    fakeApi({
      'GET /documents/kb/k1': () => slow.promise,
      'GET /documents/kb/k2': [{ id: 'd2', name: 'products.md' }],
    })
    const { result, rerender } = renderHook(({ kb }) => useDocuments(kb), {
      initialProps: { kb: 'k1' },
    })

    rerender({ kb: 'k2' })
    await waitFor(() => expect(result.current.documents).toHaveLength(1))
    await act(async () => slow.resolve([{ id: 'd1', name: 'returns.md' }]))

    expect(result.current.documents).toEqual([{ id: 'd2', name: 'products.md' }])
  })
})

describe('shared lists', () => {
  it.each([
    ['useAgents', useAgents, /AgentsProvider/],
    ['useKnowledgeBases', useKnowledgeBases, /KnowledgeBasesProvider/],
  ])('%s fails outside its provider', (_, hook, message) => {
    expect(() => renderHook(() => hook())).toThrow(message)
  })
})

describe('useOptionalTools', () => {
  it('loads the optional tools', async () => {
    fakeApi({ 'GET /tools': [{ name: 'emailTool', description: 'Sends', type: 'OPTIONAL' }] })
    const { result } = renderHook(() => useOptionalTools())

    await waitFor(() => expect(result.current.tools).toHaveLength(1))
  })
})

describe('useChatSessions', () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ChatSessionsProvider>{children}</ChatSessionsProvider>
  )

  it('fails outside the provider', () => {
    expect(() => renderHook(() => useChatSessions())).toThrow(/ChatSessionsProvider/)
  })

  it('shares one list and reloads it after each change', async () => {
    const api = fakeApi({
      'GET /chat-sessions': [{ id: 's1', title: 'Out-of-stock products' }],
      'POST /chat-sessions': { id: 's2', title: 'New chat' },
      'PATCH /chat-sessions/s1': { id: 's1', title: 'Stock' },
      'DELETE /chat-sessions/s1': null,
    })
    const { result } = renderHook(() => useChatSessions(), { wrapper })
    await waitFor(() => expect(result.current.chatSessions).toHaveLength(1))

    await act(() => result.current.create({ agentId: 'a1', title: 'New chat' }))
    await act(() => result.current.update('s1', { title: 'Stock' }))
    await act(() => result.current.remove('s1'))

    await waitFor(() => expect(count(api.calls, 'GET', '/chat-sessions')).toBe(4))
  })
})
