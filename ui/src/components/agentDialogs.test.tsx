import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { type ApiCall, fakeApi } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const storeAnalyst = {
  id: 'a1',
  name: 'Store analyst',
  description: 'Answers questions about orders.',
  model: 'deepseek-chat',
  allowedTools: [],
  allowedKbs: [],
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: null,
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-02T03:10:00',
}

function backend(extra: Record<string, (call: ApiCall) => unknown> = {}) {
  let agents: object[] = [storeAnalyst]
  const api = fakeApi({
    'GET /agents': () => agents,
    'GET /knowledge-bases': [{ id: 'k1', name: 'Store policies' }],
    'GET /chat-sessions': [],
    'GET /tools': [
      { name: 'dataBaseTool', description: 'Runs read-only SQL queries.', type: 'OPTIONAL' },
      { name: 'emailTool', description: 'Sends a plain-text email.', type: 'OPTIONAL' },
    ],
    'GET /chat-sessions/agent/a1': [{ id: 's1' }, { id: 's2' }],
    'POST /agents': ({ body }) => {
      const created = { ...storeAnalyst, ...(body as object), id: 'a2' }
      agents = [created, ...agents]
      return created
    },
    'PATCH /agents/a1': ({ body }) => {
      // Like the backend, chat options are merged field by field (ADR 015).
      const patch = body as { chatOptions?: object }
      const updated = {
        ...storeAnalyst,
        ...patch,
        chatOptions: { ...storeAnalyst.chatOptions, ...patch.chatOptions },
      }
      agents = [updated]
      return updated
    },
    'DELETE /agents/a1': () => {
      agents = []
      return null
    },
    ...extra,
  })
  return api
}

function sent(api: { calls: ApiCall[] }, method: string) {
  return api.calls.filter((c) => c.method === method)
}

async function openMenuItem(agentName: string, item: string) {
  await userEvent.click(
    await screen.findByRole('button', { name: `More actions for ${agentName}` }),
  )
  await userEvent.click(await screen.findByRole('menuitem', { name: item }))
}

describe('creating an agent', () => {
  it('sends the whole form and opens the new agent', async () => {
    const api = backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.type(within(dialog).getByLabelText('Name'), '  Trip planner ')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create agent' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Trip planner' }),
    ).toBeInTheDocument()
    expect(sent(api, 'POST')[0].body).toEqual({
      name: 'Trip planner',
      model: 'deepseek-chat',
      allowedTools: [],
      allowedKbs: [],
      chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
    })
  })

  it('asks for a name before sending anything, from any section', async () => {
    const api = backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Model' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create agent' }))

    expect(within(dialog).getByText('Enter a name.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog).getByRole('button', { name: 'Basics' })).toHaveAttribute(
      'aria-current',
      'step',
    )
    expect(sent(api, 'POST')).toEqual([])
  })

  it('keeps what was typed in one section while another is open', async () => {
    backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Trip planner')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Chat options' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Basics' }))

    expect(within(dialog).getByLabelText('Name')).toHaveValue('Trip planner')
  })

  it('adds a chosen tool, shown with its description', async () => {
    const api = backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Mailer')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Tools' }))
    await userEvent.click(within(dialog).getByRole('combobox'))
    await userEvent.click(await screen.findByText('Sends a plain-text email.'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create agent' }))

    await waitFor(() => expect(sent(api, 'POST')).toHaveLength(1))
    expect(sent(api, 'POST')[0].body).toMatchObject({ allowedTools: ['emailTool'] })
  })
})

describe('editing an agent', () => {
  it('sends only the changed fields', async () => {
    const api = backend()
    renderApp('/agent/a1')

    await openMenuItem('Store analyst', 'Edit')
    const dialog = await screen.findByRole('dialog', { name: 'Edit Store analyst' })
    const name = within(dialog).getByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, 'Shop analyst')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Chat options' }))
    // The slider reads the legacy keyCode, which user-event does not set in jsdom.
    fireEvent.keyDown(within(dialog).getByRole('slider', { name: 'Remembered messages' }), {
      key: 'ArrowRight',
      keyCode: 39,
    })
    expect(within(dialog).getByText('11')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(sent(api, 'PATCH')).toHaveLength(1))
    expect(sent(api, 'PATCH')[0].body).toEqual({
      name: 'Shop analyst',
      chatOptions: { messageLength: 11 },
    })
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Shop analyst' }),
    ).toBeInTheDocument()
  })

  it('sends nothing when nothing changed', async () => {
    const api = backend()
    renderApp('/agent/a1')

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit Store analyst' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'Edit Store analyst' })).toBeNull(),
    )
    expect(sent(api, 'PATCH')).toEqual([])
  })

  it("shows the backend's refusal and stays open", async () => {
    backend({
      'PATCH /agents/a1': () => {
        throw { code: 400, message: 'allowedTools: not an optional tool: terminate' }
      },
    })
    renderApp('/agent/a1')

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const dialog = await screen.findByRole('dialog', { name: 'Edit Store analyst' })
    await userEvent.type(within(dialog).getByLabelText('Description'), ' More.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    expect(
      await within(dialog).findByText('allowedTools: not an optional tool: terminate'),
    ).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Edit Store analyst' })).toBeInTheDocument()
  })
})

describe('deleting an agent', () => {
  it('says what happens to its chats and asks for its name', async () => {
    backend()
    renderApp('/agent/a1')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Store analyst?' })

    expect(
      await within(dialog).findByText(
        "Its 2 chats stay readable, but you can't send new messages in them. This can't be undone.",
      ),
    ).toBeInTheDocument()
    const confirm = within(dialog).getByRole('button', { name: 'Delete agent' })
    expect(confirm).toBeDisabled()
    await userEvent.type(
      within(dialog).getByLabelText('Type Store analyst to confirm'),
      'store analyst',
    )
    expect(confirm).toBeDisabled()
  })

  it('deletes after the exact name, leaves its profile, and reloads the chats', async () => {
    const api = backend()
    renderApp('/agent/a1')

    await openMenuItem('Store analyst', 'Delete')
    const dialog = await screen.findByRole('dialog', { name: 'Delete Store analyst?' })
    await userEvent.type(
      within(dialog).getByLabelText('Type Store analyst to confirm'),
      'Store analyst',
    )
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete agent' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Agents' })).toBeInTheDocument()
    expect(sent(api, 'DELETE').map((c) => c.path)).toEqual(['/agents/a1'])
    await waitFor(() =>
      expect(api.calls.filter((c) => c.path === '/chat-sessions')).toHaveLength(2),
    )
  })
})
