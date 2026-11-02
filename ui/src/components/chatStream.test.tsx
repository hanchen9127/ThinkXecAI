import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { getSseUrl } from '../api/api'
import { type ApiCall, fakeApi } from '../test/fakeApi'
import { FakeEventSource } from '../test/fakeEventSource'
import { renderApp } from '../test/renderApp'

const storeAnalyst = {
  id: 'a1',
  name: 'Store analyst',
  description: null,
  model: 'deepseek-chat',
  allowedTools: [],
  allowedKbs: [],
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: null,
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
}
const chat = (id: string, title: string) => ({
  id,
  title,
  agentId: 'a1',
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
})

const message = (id: string, role: string, content: string, second: number) => {
  const at = `2026-11-02T03:00:${String(second).padStart(2, '0')}`
  return { id, sessionId: 's1', role, content, metadata: null, createdAt: at, updatedAt: at }
}
const question = message('m1', 'user', 'Which phones are out of stock?', 1)
const answer = message('m2', 'assistant', 'One phone is out of stock.', 2)
const generated = (msg: object) => ({
  type: 'AI_GENERATED_CONTENT',
  payload: { message: msg, statusText: null, done: false },
  metadata: { chatMessageId: (msg as { id: string }).id },
})

function backend(extra: Record<string, (call: ApiCall) => unknown> = {}) {
  let stored: object[] = [question]
  const api = fakeApi({
    'GET /agents': [storeAnalyst],
    'GET /knowledge-bases': [],
    'GET /chat-sessions': () => [chat('s1', 'Out-of-stock products'), chat('s2', 'Earbud returns')],
    'GET /chat-sessions/s1': () => chat('s1', 'Out-of-stock products'),
    'GET /chat-messages/session/s1': () => stored,
    'GET /chat-messages/session/s2': [],
    'POST /chat-messages': ({ body }) => ({
      ...message('m9', 'user', '', 9),
      ...(body as object),
    }),
    ...extra,
  })
  return {
    api,
    store: (messages: object[]) => {
      stored = messages
    },
  }
}

async function openChat() {
  renderApp('/chat/s1')
  expect(await screen.findByText('Which phones are out of stock?')).toBeInTheDocument()
  await waitFor(() => expect(FakeEventSource.open()).toHaveLength(1))
  return FakeEventSource.latest()
}

const messageLoads = (api: { calls: ApiCall[] }) =>
  api.calls.filter((c) => c.path === '/chat-messages/session/s1').length

describe('live replies', () => {
  it('opens the stream for the chat and loads the history again on init', async () => {
    const { api, store } = backend()
    const source = await openChat()
    expect(source.url).toBe(getSseUrl('s1'))
    expect(source.url).toBe('http://localhost:8080/sse/connect/s1')

    // Produced while the stream was not open yet: the reload on init brings it.
    store([question, answer])
    source.emit('init', 'connected')

    expect(await screen.findByText('One phone is out of stock.')).toBeInTheDocument()
    expect(messageLoads(api)).toBe(2)
  })

  it('adds a pushed message once, even when a reload brings it too', async () => {
    const { api, store } = backend()
    const source = await openChat()
    source.emit('init', 'connected')

    source.emit('message', generated(answer))
    expect(await screen.findByText('One phone is out of stock.')).toBeInTheDocument()

    store([question, answer])
    source.emit('message', generated(answer))
    source.emit('init', 'connected')
    // Count only once the reload that brings the same message has been answered and shown.
    await waitFor(() => expect(messageLoads(api)).toBe(3))
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(screen.getAllByText('One phone is out of stock.')).toHaveLength(1)
  })

  it('ignores other event types and data it cannot read', async () => {
    backend()
    const source = await openChat()

    source.emit('message', { type: 'AI_THINKING', payload: { message: answer }, metadata: {} })
    source.emit('message', 'not json')
    source.emit('message', { type: 'AI_GENERATED_CONTENT', payload: null })
    source.emit('progress', 'later')

    expect(screen.queryByText('One phone is out of stock.')).toBeNull()
    expect(screen.getByText('Which phones are out of stock?')).toBeInTheDocument()
  })

  it('shows a sent message at once, without a reload', async () => {
    const { api } = backend()
    await openChat()
    const loads = messageLoads(api)

    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'Email it.{Enter}')

    expect(await screen.findByText('Email it.')).toBeInTheDocument()
    expect(messageLoads(api)).toBe(loads)
  })

  it('closes the stream when the chat closes, and opens one for the next chat', async () => {
    backend()
    const first = await openChat()

    await userEvent.click(screen.getByRole('link', { name: /Earbud returns/ }))

    await waitFor(() => expect(first.closed).toBe(true))
    expect(FakeEventSource.open().map((s) => s.url)).toEqual([getSseUrl('s2')])
  })

  it('opens no stream for a chat that does not exist', async () => {
    backend()
    renderApp('/chat/s7')
    expect(await screen.findByRole('heading', { name: 'Chat not found' })).toBeInTheDocument()
    expect(FakeEventSource.instances).toEqual([])
  })
})

describe('the chat open in another tab', () => {
  it('closes without reconnecting and offers to take the chat back', async () => {
    const { api } = backend()
    const source = await openChat()
    source.emit('init', 'connected')

    source.emit('replaced', 'replaced')

    expect(source.closed).toBe(true)
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(await screen.findByText('This chat is open in another tab.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: 'Message' })).toBeNull()

    const loads = messageLoads(api)
    await userEvent.click(screen.getByRole('button', { name: 'Use it here' }))
    const again = FakeEventSource.latest()
    expect(again).not.toBe(source)
    expect(again.url).toBe(getSseUrl('s1'))
    again.emit('init', 'connected')

    expect(await screen.findByRole('textbox', { name: 'Message' })).toBeInTheDocument()
    await waitFor(() => expect(messageLoads(api)).toBe(loads + 1))
  })
})

describe('when the stream ends', () => {
  it('leaves a chat that was deleted elsewhere and says so', async () => {
    const { api } = backend({
      'GET /chat-sessions/s1': () => {
        throw { code: 404, message: 'Chat session not found' }
      },
    })
    const source = await openChat()

    source.fail('gave up')

    expect(await screen.findByText('The chat you had open was deleted.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: /look into\?$/ })).toBeInTheDocument()
    expect(api.calls.filter((c) => c.path === '/chat-sessions').length).toBeGreaterThan(1)
  })

  it('offers to try again when the chat still exists', async () => {
    backend()
    const source = await openChat()

    source.fail('gave up')

    const banner = await screen.findByRole('alert')
    expect(banner).toHaveTextContent('Live replies stopped. Messages you send are still saved.')
    expect(screen.getByRole('textbox', { name: 'Message' })).toBeEnabled()

    await userEvent.click(within(banner).getByRole('button', { name: 'Try again' }))
    const again = FakeEventSource.latest()
    expect(again).not.toBe(source)
    again.emit('init', 'connected')
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  })

  it("says when the server can't be reached to check the chat", async () => {
    backend()
    const fake = globalThis.fetch
    vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) =>
      String(input).endsWith('/chat-sessions/s1')
        ? Promise.reject(new TypeError('Failed to fetch'))
        : fake(input, init),
    )
    const source = await openChat()

    source.fail('gave up')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      // Not "still saved": sending fails too while the server is out of reach.
      /^Live replies stopped\. Can't reach the server\.\s*Try again$/,
    )
  })

  it('waits quietly while the browser retries by itself', async () => {
    const { api } = backend()
    const source = await openChat()

    source.fail('retrying')

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(screen.queryByRole('alert')).toBeNull()
    expect(api.calls.filter((c) => c.path === '/chat-sessions/s1')).toEqual([])
    expect(source.closed).toBe(false)
  })
})
