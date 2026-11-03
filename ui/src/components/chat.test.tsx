import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { type ApiCall, deferred, fakeApi } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const agent = (id: string, name: string) => ({
  id,
  name,
  description: null,
  model: 'deepseek-chat',
  allowedTools: [],
  allowedKbs: [],
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: null,
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
})
const storeAnalyst = agent('a1', 'Store analyst')
const supportDesk = agent('a2', 'Support desk')

const session = (id: string, title: string | null, agentId: string | null, updatedAt: string) => ({
  id,
  title,
  agentId,
  createdAt: updatedAt,
  updatedAt,
})

let clock = 0
const msg = (role: string, content: string | null, metadata: object | null = null) => {
  clock += 1
  const at = `2026-11-02T03:00:${String(clock).padStart(2, '0')}`
  return { id: `m${clock}`, sessionId: 's1', role, content, metadata, createdAt: at, updatedAt: at }
}
const toolCall = (id: string, name: string, args: string) => ({
  toolCalls: [{ id, type: 'function', name, arguments: args }],
})
const toolResult = (id: string, name: string, data: string) =>
  msg('tool', null, { toolResponse: { id, name, responseData: data } })

function history() {
  clock = 0
  return [
    msg('user', 'Which phones are out of stock, and when do they come back?'),
    msg(
      'assistant',
      'I will find the phones that are out of stock.',
      toolCall('c1', 'dataBaseTool', '{"sql":"SELECT sku FROM t_product;"}'),
    ),
    toolResult(
      'c1',
      'dataBaseTool',
      JSON.stringify('| sku | name |\n|---|---|\n| SP-NX12L | Nova X12 Lite |'),
    ),
    msg('assistant', 'Now the date.', toolCall('c2', 'dateTool', '{}')),
    toolResult('c2', 'dateTool', '{"date":"2026-11-02"}'),
    msg(
      'assistant',
      'Now the restock rule.',
      toolCall('c3', 'knowledgeSearch', '{"query":"restock time"}'),
    ),
    toolResult('c3', 'knowledgeSearch', 'not json at all'),
    msg('assistant', 'One phone is out of stock: **Nova X12 Lite**.'),
  ]
}

function backend(extra: Record<string, (call: ApiCall) => unknown> = {}) {
  let sessions = [
    session('s1', 'Out-of-stock products', 'a1', '2026-11-01T23:00:00'),
    session('s2', 'Earbud returns', 'a2', '2026-10-30T03:00:00'),
    session('s3', null, null, '2026-10-28T03:00:00'),
  ]
  const api = fakeApi({
    'GET /agents': [storeAnalyst, supportDesk],
    'GET /knowledge-bases': [],
    'GET /chat-sessions': () => sessions,
    'GET /chat-messages/session/s1': history,
    'GET /chat-messages/session/s3': [],
    'GET /chat-messages/session/s9': [],
    'POST /chat-sessions': ({ body }) => {
      const created = { ...session('s9', null, null, '2026-11-02T04:00:00'), ...(body as object) }
      sessions = [created, ...sessions]
      return created
    },
    'POST /chat-messages': ({ body }) => ({ ...msg('user', null), ...(body as object) }),
    'PATCH /chat-sessions/s2': ({ body }) => {
      const updated = { ...sessions[1], ...(body as object) }
      sessions = sessions.map((s) => (s.id === 's2' ? updated : s))
      return updated
    },
    'DELETE /chat-sessions/s1': () => {
      sessions = sessions.filter((s) => s.id !== 's1')
      return null
    },
    ...extra,
  })
  return api
}

function posts(api: { calls: ApiCall[] }) {
  return api.calls.filter((c) => c.method === 'POST').map((c) => [c.path, c.body])
}

describe('a new chat', () => {
  it('creates the chat, then the message, exactly once, then opens it', async () => {
    const message = deferred<void>()
    const api = backend({
      'POST /chat-messages': async ({ body }) => {
        await message.promise
        return { ...msg('user', null), ...(body as object) }
      },
    })
    renderApp('/chat')

    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'What should Store analyst look into?',
      }),
    ).toBeInTheDocument()
    const box = screen.getByRole('textbox', { name: 'Message' })
    await userEvent.type(box, 'Which phones are out of stock?{Enter}')
    await userEvent.type(box, '{Enter}')
    await userEvent.click(screen.getByRole('button', { name: /Send/ }))

    message.resolve()
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Which phones are out of stock?' }),
    ).toBeInTheDocument()
    expect(posts(api)).toEqual([
      ['/chat-sessions', { agentId: 'a1', title: 'Which phones are out of stock?' }],
      ['/chat-messages', { sessionId: 's9', content: 'Which phones are out of stock?' }],
    ])
  })

  it('starts with the agent named in the address, and can change it', async () => {
    const api = backend()
    renderApp('/chat?agent=a2')

    expect(
      await screen.findByRole('heading', { name: 'What should Support desk look into?' }),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('combobox', { name: 'Agent' }))
    await userEvent.click(await screen.findByRole('option', { name: 'Store analyst' }))
    expect(
      screen.getByRole('heading', { name: 'What should Store analyst look into?' }),
    ).toBeInTheDocument()

    await userEvent.type(screen.getByRole('textbox', { name: 'Message' }), 'Hi{Enter}')
    await waitFor(() =>
      expect(posts(api)[0]).toEqual(['/chat-sessions', { agentId: 'a1', title: 'Hi' }]),
    )
  })

  it('keeps the text and reuses the new chat when the message fails', async () => {
    let fail = true
    const api = backend({
      'POST /chat-messages': ({ body }) => {
        if (fail) throw { code: 500, message: 'Internal server error' }
        return { ...msg('user', null), ...(body as object) }
      },
    })
    renderApp('/chat')

    const box = await screen.findByRole('textbox', { name: 'Message' })
    await userEvent.type(box, 'Hello{Enter}')
    expect(await screen.findByRole('alert')).toHaveTextContent('Internal server error')
    expect(box).toHaveValue('Hello')

    fail = false
    await userEvent.type(box, '{Enter}')
    expect(await screen.findByRole('heading', { level: 1, name: 'Hello' })).toBeInTheDocument()
    expect(posts(api).map(([p]) => p)).toEqual([
      '/chat-sessions',
      '/chat-messages',
      '/chat-messages',
    ])
  })

  it('points to creating an agent when there is none', async () => {
    fakeApi({ 'GET /agents': [], 'GET /knowledge-bases': [], 'GET /chat-sessions': [] })
    renderApp('/chat')
    expect(await screen.findByRole('link', { name: 'Create an agent' })).toHaveAttribute(
      'href',
      '/agent',
    )
    expect(screen.queryByRole('textbox', { name: 'Message' })).toBeNull()
  })

  it('sends nothing blank, and Shift+Enter starts a new line', async () => {
    const api = backend()
    renderApp('/chat')

    const box = await screen.findByRole('textbox', { name: 'Message' })
    await userEvent.type(box, '   {Enter}')
    expect(posts(api)).toEqual([])
    await userEvent.clear(box)
    await userEvent.type(box, 'one{Shift>}{Enter}{/Shift}two')
    expect(box).toHaveValue('one\ntwo')
    expect(posts(api)).toEqual([])
  })
})

describe('a chat', () => {
  it('shows the question, the last step, and the answer', async () => {
    backend()
    renderApp('/chat/s1')

    const heading = await screen.findByRole('heading', { level: 1, name: 'Out-of-stock products' })
    expect(heading.nextElementSibling).toHaveTextContent(/^Store analyst on deepseek-chat$/)
    expect(
      await screen.findByText('Which phones are out of stock, and when do they come back?'),
    ).toBeInTheDocument()

    const run = screen.getByRole('article', { name: 'Store analyst' })
    const steps = within(run).getByRole('list', { name: 'Agent steps' })
    expect(within(steps).getByText('knowledgeSearch').closest('p')).toHaveTextContent(
      /^Step 3, Execute: knowledgeSearch$/,
    )
    expect(within(steps).queryByText('Step 1, Think')).toBeNull()
    expect(within(run).getByText('Nova X12 Lite').tagName).toBe('STRONG')

    await userEvent.click(within(steps).getByRole('button', { name: 'Show 2 earlier steps' }))
    expect(within(steps).getByText('Step 1, Think')).toBeInTheDocument()
    expect(within(steps).getByText('sql: SELECT sku FROM t_product;')).toBeInTheDocument()
  })

  it('opens each tool result only when asked, decoded', async () => {
    backend()
    renderApp('/chat/s1')

    const steps = await screen.findByRole('list', { name: 'Agent steps' })
    await userEvent.click(within(steps).getByRole('button', { name: 'Show 2 earlier steps' }))
    expect(within(steps).queryByRole('table')).toBeNull()

    const [table, json, raw] = within(steps).getAllByRole('button', { name: 'Show the result' })
    await userEvent.click(table)
    expect(within(steps).getByRole('cell', { name: 'Nova X12 Lite' })).toBeInTheDocument()
    await userEvent.click(json)
    expect(within(steps).getByText(/"date": "2026-11-02"/)).toBeInTheDocument()
    await userEvent.click(raw)
    expect(within(steps).getByText('not json at all')).toBeInTheDocument()
    expect(within(steps).getAllByRole('button', { name: 'Hide the result' })).toHaveLength(3)
  })

  it('sends a message and moves the chat up the list', async () => {
    const api = backend({
      'POST /chat-messages': ({ body }) => ({ ...msg('user', null), ...(body as object) }),
    })
    renderApp('/chat/s1')

    await userEvent.type(
      await screen.findByRole('textbox', { name: 'Message' }),
      'Email it.{Enter}',
    )
    await waitFor(() =>
      expect(posts(api)).toEqual([['/chat-messages', { sessionId: 's1', content: 'Email it.' }]]),
    )
    await waitFor(() =>
      expect(api.calls.filter((c) => c.path === '/chat-sessions')).toHaveLength(2),
    )
    expect(screen.getByRole('textbox', { name: 'Message' })).toHaveValue('')
  })

  it('says a chat whose agent was deleted can be read but not continued', async () => {
    backend()
    renderApp('/chat/s3')

    expect(
      await screen.findByText('Its agent was deleted. You can read this chat but not continue it.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Untitled chat' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Message' })).toBeDisabled()
    expect(screen.queryByText('Enter to send, Shift+Enter for a new line')).not.toBeInTheDocument()
  })

  it('shows the refusal when the agent went away meanwhile', async () => {
    backend({
      'POST /chat-messages': () => {
        throw { code: 409, message: 'Chat session has no agent' }
      },
    })
    renderApp('/chat/s1')

    const box = await screen.findByRole('textbox', { name: 'Message' })
    await userEvent.type(box, 'Hi{Enter}')
    expect(await screen.findByRole('alert')).toHaveTextContent('Chat session has no agent')
    expect(box).toHaveValue('Hi')
  })

  it('reloads the chats and agents after that refusal, so the chat shows its agent is gone', async () => {
    let agentDeleted = false
    const api = backend({
      'GET /chat-sessions': () => [
        session('s1', 'Out-of-stock products', agentDeleted ? null : 'a1', '2026-11-01T23:00:00'),
      ],
      'GET /agents': () => (agentDeleted ? [supportDesk] : [storeAnalyst, supportDesk]),
      'POST /chat-messages': () => {
        agentDeleted = true
        throw { code: 409, message: 'Chat session has no agent' }
      },
    })
    renderApp('/chat/s1')
    const box = await screen.findByRole('textbox', { name: 'Message' })
    const loads = (path: string) => api.calls.filter((c) => c.method === 'GET' && c.path === path)
    await waitFor(() => expect(loads('/agents')).toHaveLength(1))
    const sessionLoads = loads('/chat-sessions').length

    await userEvent.type(box, 'Hi{Enter}')

    expect(
      await screen.findByText('Its agent was deleted. You can read this chat but not continue it.'),
    ).toBeInTheDocument()
    expect(loads('/agents')).toHaveLength(2)
    expect(loads('/chat-sessions').length).toBeGreaterThan(sessionLoads)
    expect(box).toBeDisabled()
  })
})

describe('the chat list', () => {
  it('lists title, agent, and time, with untitled and agentless chats named', async () => {
    backend()
    renderApp('/chat')

    const list = await screen.findByRole('region', { name: 'Chats' })
    const items = await within(list).findAllByRole('listitem')
    expect(items.map((li) => li.querySelector('a')?.textContent)).toEqual([
      expect.stringMatching(/^Out-of-stock productsStore analyst, /),
      'Earbud returnsSupport desk, Oct 30',
      'Untitled chatNo agent, Oct 28',
    ])
  })

  it('filters by title or agent name from the search button', async () => {
    backend()
    renderApp('/chat')

    const list = await screen.findByRole('region', { name: 'Chats' })
    await within(list).findAllByRole('listitem')
    await userEvent.click(within(list).getByRole('button', { name: 'Search chats' }))
    const search = within(list).getByRole('textbox', { name: 'Search chats' })

    await userEvent.type(search, 'support')
    expect(within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(within(list).getByRole('status')).toHaveTextContent('1 of 3 chats')

    await userEvent.clear(search)
    await userEvent.type(search, 'STOCK')
    expect(within(list).getByRole('link', { name: /Out-of-stock products/ })).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')
    expect(within(list).queryByRole('textbox', { name: 'Search chats' })).toBeNull()
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
  })

  it('renames a chat in its row', async () => {
    const api = backend()
    renderApp('/chat')

    await userEvent.click(
      await screen.findByRole('button', { name: 'More actions for Earbud returns' }),
    )
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Rename' }))
    const field = screen.getByRole('textbox', { name: 'Title of Earbud returns' })
    await userEvent.clear(field)
    await userEvent.type(field, 'Returns{Enter}')

    expect(await screen.findByRole('link', { name: /^Returns/ })).toBeInTheDocument()
    expect(api.calls.filter((c) => c.method === 'PATCH')).toEqual([
      { method: 'PATCH', path: '/chat-sessions/s2', body: { title: 'Returns' } },
    ])
  })

  it('deletes the open chat after a confirmation, with Undo, and leaves it', async () => {
    const api = backend()
    renderApp('/chat/s1')

    await userEvent.click(
      await screen.findByRole('button', { name: 'More actions for Out-of-stock products' }),
    )
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }))
    const confirmation = await screen.findByRole('tooltip')
    expect(confirmation).toHaveTextContent('Its messages are deleted too.')
    await userEvent.click(within(confirmation).getByRole('button', { name: 'Delete' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: /look into\?$/ }),
    ).toBeInTheDocument()
    const list = screen.getByRole('region', { name: 'Chats' })
    expect(within(list).getByRole('status')).toHaveTextContent('Out-of-stock products deleted')
    expect(within(list).queryByRole('link', { name: /Out-of-stock products/ })).toBeNull()

    await userEvent.click(within(list).getByRole('button', { name: 'Undo' }))
    expect(within(list).getByRole('link', { name: /Out-of-stock products/ })).toBeInTheDocument()
    expect(api.calls.filter((c) => c.method === 'DELETE')).toEqual([])
  })

  it('sends a waiting delete when the list closes', async () => {
    const api = backend()
    renderApp('/chat')

    await userEvent.click(
      await screen.findByRole('button', { name: 'More actions for Out-of-stock products' }),
    )
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }))
    await userEvent.click(
      within(await screen.findByRole('tooltip')).getByRole('button', { name: 'Delete' }),
    )
    await userEvent.click(screen.getByRole('link', { name: 'Agents' }))
    // Navigation waits for the next page's lazy code, so the list closes a moment after the click.
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Chats' })).toBeNull())

    expect(api.calls.filter((c) => c.method === 'DELETE')).toEqual([
      { method: 'DELETE', path: '/chat-sessions/s1', body: undefined },
    ])
  })
})
