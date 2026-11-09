import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { getSseUrl } from '../api/api'
import { type ApiCall, deferred, fakeApi } from '../test/fakeApi'
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
      /^Live replies stopped\. Can't reach the server\. Check that it is running, then try again\.\s*Try again$/,
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

describe('the working indicator', () => {
  /** An API timestamp `ago` ms before the real clock (the indicator counts from message times). */
  const stamp = (ago: number) => new Date(Date.now() - ago).toISOString().slice(0, -1)
  const recent = (id: string, role: string, ago: number, metadata: object | null = null) => ({
    ...message(id, role, 'x', 0),
    content: role === 'tool' ? null : `${role} ${id}`,
    metadata,
    createdAt: stamp(ago),
    updatedAt: stamp(ago),
  })
  const calls = { toolCalls: [{ id: 'c1', type: 'function', name: 'dateTool', arguments: '{}' }] }
  const notice = (id: string, ago: number) => ({
    ...recent(id, 'assistant', ago),
    content: 'Stopped.',
  })
  /** What was posted as new messages. */
  const sent = (api: { calls: ApiCall[] }) =>
    api.calls
      .filter((call) => call.method === 'POST' && call.path === '/chat-messages')
      .map((call) => (call.body as { content: string }).content)

  it('shows the agent working after a message is sent, and stops at the answer', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 60_000),
        recent('m2', 'assistant', 50_000),
      ],
      'POST /chat-messages': ({ body }) => ({ ...recent('m3', 'user', 0), ...(body as object) }),
    })
    renderApp('/chat/s1')
    const box = await screen.findByRole('textbox', { name: 'Message' })
    await screen.findByText('assistant m2')
    expect(screen.queryByText(/is working/)).toBeNull()

    await userEvent.type(box, 'Email it.{Enter}')

    expect(await screen.findByText('Store analyst is working')).toBeInTheDocument()
    expect(box).toBeEnabled()

    FakeEventSource.latest().emit('message', generated(recent('m4', 'assistant', 0)))
    await waitFor(() => expect(screen.queryByText(/is working/)).toBeNull())
  })

  it('shows it, with the steps so far, when the opened chat ends with a tool step', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 90_000),
        recent('m2', 'assistant', 60_000, calls),
      ],
    })
    renderApp('/chat/s1')

    // Every step shows while it works (its Think and its Execute), then the waiting row's timer,
    // counted from the newest message a minute ago.
    const run = await screen.findByRole('article', { name: 'Store analyst' })
    const status = await within(run).findByRole('status')
    expect(status).toHaveTextContent('Store analyst is working, 2 steps so far')
    expect(within(status).getByText(/^1:0\d$/)).toBeInTheDocument()
    expect(within(run).getByText('No result yet')).toBeInTheDocument()
  })

  it('shows nothing for a run that died long ago', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [
        {
          ...recent('m1', 'user', 0),
          createdAt: '2020-01-01T00:00:00',
          updatedAt: '2020-01-01T00:00:00',
        },
      ],
    })
    renderApp('/chat/s1')

    expect(await screen.findByText('user m1')).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(screen.queryByText(/is working/)).toBeNull()
  })

  it('lets only a step that arrives live fade in (ADR 061)', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 90_000),
        recent('m2', 'assistant', 60_000, calls),
      ],
    })
    renderApp('/chat/s1')
    const steps = await screen.findByRole('list', { name: 'Agent steps' })
    const loaded = within(steps).getAllByRole('listitem')
    expect(loaded.filter((row) => row.hasAttribute('data-fresh'))).toEqual([])

    const next = { toolCalls: [{ id: 'c2', type: 'function', name: 'cityTool', arguments: '{}' }] }
    FakeEventSource.latest().emit('message', generated(recent('m3', 'assistant', 0, next)))

    const row = (await within(steps).findByText('cityTool')).closest('li')
    expect(row).toHaveAttribute('data-fresh')
    // The Execute row before it now draws the rail down to it.
    expect(within(steps).getByText('dateTool').closest('li')).toHaveAttribute('data-grows')
  })

  it('shows Stop beside Send while it works, and keeps it while the user types', async () => {
    backend({ 'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)] })
    renderApp('/chat/s1')

    const stop = await screen.findByRole('button', { name: 'Stop' })
    expect(stop).toBeEnabled()
    const send = screen.getByRole('button', { name: 'Send' })
    expect(send).toBeDisabled()
    // A correction typed during a run can still stop it, or be queued with Send (ADR 060).
    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'And the price?')
    expect(stop).toBeEnabled()
    expect(stop).toBeInTheDocument()
    expect(send).toBeEnabled()
  })

  it('puts focus back in the message box when the Stop it was on goes', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)],
      'POST /chat-sessions/s1/stop': () => false,
    })
    renderApp('/chat/s1')

    await userEvent.click(await screen.findByRole('button', { name: 'Stop' }))

    await waitFor(() => expect(screen.queryByRole('button', { name: /^Stop/ })).toBeNull())
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveFocus()
  })

  it('stops the session, says Stopping… until the notice, then offers Try again', async () => {
    const { api } = backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)],
      'POST /chat-sessions/s1/stop': () => true,
      'POST /chat-messages': ({ body }) => ({ ...recent('m3', 'user', 0), ...(body as object) }),
    })
    renderApp('/chat/s1')

    await userEvent.click(await screen.findByRole('button', { name: 'Stop' }))
    expect(api.calls.filter((c) => c.path === '/chat-sessions/s1/stop')).toHaveLength(1)
    const stopping = screen.getByRole('button', { name: 'Stopping…' })
    expect(stopping).toHaveAttribute('aria-disabled', 'true')
    expect(stopping).toHaveClass('opacity-60')
    // It keeps focus, but a second click sends no second stop.
    await userEvent.click(stopping)
    expect(api.calls.filter((c) => c.path === '/chat-sessions/s1/stop')).toHaveLength(1)

    // Typing while it stops leaves Stopping… in place and gives a Send, to queue a message.
    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'And the price?')
    expect(stopping).toHaveAccessibleName('Stopping…')
    expect(stopping).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled()
    await userEvent.clear(screen.getByRole('textbox', { name: 'Message' }))

    const stopped = { ...recent('m2', 'assistant', 0), content: 'Stopped.' }
    FakeEventSource.latest().emit('message', generated(stopped))
    expect(await screen.findByText('Stopped.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Stop/ })).toBeNull()
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() =>
      expect(api.calls.filter((c) => c.method === 'POST' && c.path === '/chat-messages')).toEqual([
        {
          method: 'POST',
          path: '/chat-messages',
          body: { sessionId: 's1', content: 'user m1' },
        },
      ]),
    )
    // Used, it goes away, and the new run can be stopped: "Stopping…" ended with the last run.
    expect(await screen.findByRole('button', { name: 'Stop' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('ends the run at once when a stop finds none, as after a backend restart', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)],
      'POST /chat-sessions/s1/stop': () => false,
    })
    renderApp('/chat/s1')

    await userEvent.click(await screen.findByRole('button', { name: 'Stop' }))

    expect(await screen.findByRole('button', { name: 'Send' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: /^Stop/ })).toBeNull()
    expect(screen.queryByText(/is working/)).toBeNull()

    // A later message means a run again.
    FakeEventSource.latest().emit('message', generated(recent('m2', 'assistant', 0, calls)))
    expect(await screen.findByRole('button', { name: 'Stop' })).toBeEnabled()
  })

  it('shows why a stop failed and offers Stop again', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)],
      'POST /chat-sessions/s1/stop': () => {
        throw { code: 404, message: 'Chat session not found' }
      },
    })
    renderApp('/chat/s1')

    await userEvent.click(await screen.findByRole('button', { name: 'Stop' }))

    // a failed stop says so, not that a message failed to send
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent("Couldn't stop the agent.")
    expect(alert).toHaveTextContent('It no longer exists; it may have been deleted in another tab.')
    expect(screen.getByRole('button', { name: 'Stop' })).toBeEnabled()
  })

  it('offers Try again on a failed run but not on an answer', async () => {
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 30_000),
        { ...recent('m2', 'assistant', 20_000), content: 'One phone is out of stock.' },
        recent('m3', 'user', 10_000),
        {
          ...recent('m4', 'assistant', 5_000),
          content: 'The agent could not finish this request: the agent no longer exists.',
        },
      ],
    })
    renderApp('/chat/s1')

    expect(
      await screen.findByText(
        'The agent could not finish this request: the agent no longer exists.',
      ),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Try again' })).toHaveLength(1)
  })

  it('offers Try again once, after the last notice of the latest stopped run', async () => {
    const { api } = backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 60_000),
        notice('m2', 50_000),
        recent('m3', 'user', 40_000),
        recent('m4', 'user', 30_000),
        notice('m5', 20_000),
        notice('m6', 10_000),
      ],
      'POST /chat-messages': ({ body }) => ({ ...recent('m7', 'user', 0), ...(body as object) }),
    })
    renderApp('/chat/s1')

    await waitFor(() => expect(screen.getAllByText('Stopped.')).toHaveLength(3))
    const retry = screen.getAllByRole('button', { name: 'Try again' })
    expect(retry).toHaveLength(1)
    for (const notice of screen.getAllByText('Stopped.')) {
      expect(notice.compareDocumentPosition(retry[0])).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    }
    expect(retry[0].closest('article')).toContainElement(screen.getAllByText('Stopped.').at(-1)!)

    await userEvent.click(retry[0])
    await waitFor(() => expect(sent(api)).toEqual(['user m4']))
  })

  it('sends a question once when Try again is clicked twice', async () => {
    const stored = deferred<object>()
    const { api } = backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 20_000), notice('m2', 10_000)],
      'POST /chat-messages': () => stored.promise,
    })
    renderApp('/chat/s1')

    // The second click lands while the first is still being sent, with the button still there.
    const retry = await screen.findByRole('button', { name: 'Try again' })
    await userEvent.click(retry)
    await userEvent.click(retry)
    stored.resolve({ ...recent('m3', 'user', 0), content: 'user m1' })

    await waitFor(() => expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull())
    expect(sent(api)).toEqual(['user m1'])
  })

  it('keeps Try again after a send that failed', async () => {
    let refused = true
    const { api } = backend({
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 20_000), notice('m2', 10_000)],
      'POST /chat-messages': ({ body }) => {
        if (refused) {
          refused = false
          throw { code: 500, message: 'Not saved' }
        }
        return { ...recent('m3', 'user', 0), ...(body as object) }
      },
    })
    renderApp('/chat/s1')

    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't send your message.")

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(sent(api)).toEqual(['user m1', 'user m1']))
  })

  it.each([
    ['the next question is still running', ['m1', 'm3', 'm4']],
    ['the next question was answered', ['m1', 'm3', 'm4', 'm5']],
    ['the stopped run had a step, and the next question is running', ['m1', 'm2', 'm3', 'm4']],
    ['the next question has taken a step', ['m1', 'm2', 'm3', 'm4', 'm6']],
    [
      'an earlier run replied twice, and a question is still running',
      ['m1', 'm7', 'm8', 'm3', 'm9', 'm4'],
    ],
  ])('offers no Try again on a notice when %s', async (_, ids) => {
    const stored = {
      m1: recent('m1', 'user', 40_000),
      m2: recent('m2', 'assistant', 35_000, calls),
      m3: recent('m3', 'user', 30_000),
      m4: notice('m4', 20_000),
      m5: { ...recent('m5', 'assistant', 10_000), content: 'The price is 20.' },
      m6: recent('m6', 'assistant', 10_000, calls),
      m7: recent('m7', 'assistant', 38_000),
      m8: recent('m8', 'assistant', 36_000),
      m9: recent('m9', 'user', 25_000),
    }
    backend({
      'GET /chat-messages/session/s1': () => ids.map((id) => stored[id as keyof typeof stored]),
    })
    renderApp('/chat/s1')

    expect(await screen.findByText('Stopped.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('says a call stopped before it ran was not run, and one still running has no result yet', async () => {
    // The stop came between Think and Execute: the call is stored, its result never is (ADR 060).
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 20_000),
        recent('m2', 'assistant', 15_000, calls),
        notice('m3', 10_000),
      ],
    })
    const { unmount } = renderApp('/chat/s1')
    expect(await screen.findByText('Stopped.')).toBeInTheDocument()
    expect(screen.getByText('Not run')).toBeInTheDocument()
    expect(screen.queryByText('No result yet')).toBeNull()
    unmount()

    // Still working on that call: its result is on its way.
    backend({
      'GET /chat-messages/session/s1': () => [
        recent('m1', 'user', 20_000),
        recent('m2', 'assistant', 15_000, calls),
      ],
    })
    renderApp('/chat/s1')
    expect(await screen.findByText('No result yet')).toBeInTheDocument()
    expect(screen.queryByText('Not run')).toBeNull()
  })

  it('offers no Try again once the agent is gone', async () => {
    backend({
      'GET /chat-sessions': () => [{ ...chat('s1', 'Pricing'), agentId: null }],
      'GET /chat-messages/session/s1': () => [recent('m1', 'user', 20_000), notice('m2', 10_000)],
    })
    renderApp('/chat/s1')

    expect(await screen.findByText('Stopped.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('presses every button through the shared config, not a class override (ADR 061)', async () => {
    backend({ 'GET /chat-messages/session/s1': () => [recent('m1', 'user', 1_000)] })
    renderApp('/chat/s1')

    expect(await screen.findByRole('button', { name: 'Stop' })).toHaveClass(
      'not-disabled:active:scale-97',
    )
  })
})
