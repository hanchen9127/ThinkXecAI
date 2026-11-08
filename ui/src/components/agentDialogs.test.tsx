import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { type ApiCall, fakeApi } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'
import { TOOLS } from '../test/tools'

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
    'GET /tools': TOOLS,
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

describe('focus around the dialogs (Phase 14, D6)', () => {
  it('goes back to New agent when its dialog closes with Esc', async () => {
    backend()
    renderApp('/agent')

    const opener = await screen.findByRole('button', { name: 'New agent' })
    await userEvent.click(opener)
    await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.keyboard('{Escape}')

    await waitFor(() => expect(opener).toHaveFocus())
  })

  it('goes back to the menu button after a dialog opened from its menu closes', async () => {
    backend()
    renderApp('/agent/a1')

    const opener = await screen.findByRole('button', { name: 'More actions' })
    await userEvent.click(opener)
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: /Delete/ })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(opener).toHaveFocus())
  })

  it("goes back to a list row's menu button after its Edit dialog closes", async () => {
    backend()
    renderApp('/agent')

    await openMenuItem('Store analyst', 'Edit')
    const dialog = await screen.findByRole('dialog', { name: 'Edit Store analyst' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'More actions for Store analyst' })).toHaveFocus(),
    )
  })

  it('leaves focus to the new page when saving opened one', async () => {
    backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Trip planner')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create agent' }))

    const heading = await screen.findByRole('heading', { level: 1, name: 'Trip planner' })
    await waitFor(() => expect(heading).toHaveFocus())
  })
})

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

  it('offers the optional tools by label, with their descriptions, and names the rest', async () => {
    const api = backend()
    renderApp('/agent')

    await userEvent.click(await screen.findByRole('button', { name: 'New agent' }))
    const dialog = await screen.findByRole('dialog', { name: 'New agent' })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Mailer')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Tools' }))
    expect(
      within(dialog).getByText(
        'Every agent also has: Knowledge search, Sample city, Date, Sample weather.',
      ),
    ).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('combobox'))
    // only what can be chosen: a tool every agent has is not an option
    expect(await screen.findByText('Email.')).toBeInTheDocument()
    expect(screen.queryByText('Date.')).toBeNull()
    await userEvent.click(screen.getByText('Email.'))
    expect(within(dialog).getByRole('button', { name: 'Remove Email' })).toBeInTheDocument()
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
  it('puts the cursor in the name field of the delete dialog', async () => {
    backend()
    renderApp('/agent/a1')

    await userEvent.click(await screen.findByRole('button', { name: 'More actions' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }))

    const dialog = await screen.findByRole('dialog', { name: 'Delete Store analyst?' })
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Type Store analyst to confirm')).toHaveFocus(),
    )
  })

  it('says what happens to its chats and asks for its name', async () => {
    backend()
    renderApp('/agent/a1')

    await userEvent.click(await screen.findByRole('button', { name: 'More actions' }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }))
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
