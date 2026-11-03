import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { type ApiCall, deferred, fakeApi } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const storePolicies = {
  id: 'k1',
  name: 'Store policies',
  description: 'Returns, opening hours and delivery rules',
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
}
const manuals = { ...storePolicies, id: 'k2', name: 'Product manuals', description: null }

const agent = (id: string, name: string, allowedKbs: string[]) => ({
  id,
  name,
  description: null,
  model: 'deepseek-chat',
  allowedTools: [],
  allowedKbs,
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: null,
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
})

const doc = (id: string, name: string, createdAt: string) => ({
  id,
  kbId: 'k1',
  name,
  metadata: null,
  createdAt,
  updatedAt: createdAt,
})

function backend(extra: Record<string, (call: ApiCall) => unknown> = {}) {
  let knowledgeBases: object[] = [storePolicies, manuals]
  let agents = [
    agent('a1', 'Store analyst', ['k1']),
    agent('a2', 'Support desk', ['k1', 'k2']),
    agent('a3', 'Trip planner', []),
  ]
  // The backend orders by last change; the page shows the newest upload first.
  let documents = [
    doc('d1', 'opening-hours.md', '2026-11-02T02:48:00'),
    doc('d2', 'returns-policy.md', '2026-11-02T03:05:00'),
  ]
  const api = fakeApi({
    'GET /agents': () => agents,
    'GET /knowledge-bases': () => knowledgeBases,
    'GET /chat-sessions': [],
    'GET /documents/kb/k1': () => documents,
    'GET /documents/kb/k2': [],
    'POST /knowledge-bases': ({ body }) => {
      const created = { ...storePolicies, description: null, ...(body as object), id: 'k3' }
      knowledgeBases = [created, ...knowledgeBases]
      return created
    },
    'PATCH /knowledge-bases/k1': ({ body }) => {
      const updated = { ...storePolicies, ...(body as object) }
      knowledgeBases = [updated, manuals]
      return updated
    },
    'DELETE /knowledge-bases/k1': () => {
      knowledgeBases = [manuals]
      agents = agents.map((a) => ({ ...a, allowedKbs: a.allowedKbs.filter((id) => id !== 'k1') }))
      return null
    },
    'POST /documents/upload': ({ body }) => {
      const file = (body as FormData).get('file') as File
      const created = doc('d3', file.name, '2026-11-02T04:00:00')
      documents = [created, ...documents]
      return created
    },
    'PATCH /documents/d1': ({ body }) => {
      const updated = { ...documents[0], ...(body as object) }
      documents = documents.map((d) => (d.id === 'd1' ? updated : d))
      return updated
    },
    'DELETE /documents/d1': () => {
      documents = documents.filter((d) => d.id !== 'd1')
      return null
    },
    ...extra,
  })
  return api
}

function sent(api: { calls: ApiCall[] }, method: string, path?: string) {
  return api.calls.filter((c) => c.method === method && (path === undefined || c.path === path))
}

function fileInput() {
  return document.querySelector<HTMLInputElement>('input[type="file"]')!
}

// Dropping a file skips the picker's `accept` filter, so the tests do too.
const user = () => userEvent.setup({ applyAccept: false })

async function openMenuItem(name: string, item: string) {
  await userEvent.click(await screen.findByRole('button', { name: `More actions for ${name}` }))
  await userEvent.click(await screen.findByRole('menuitem', { name: item }))
}

async function confirmDocumentDelete(name: string) {
  await openMenuItem(name, 'Delete')
  const confirmation = await screen.findByRole('tooltip')
  await userEvent.click(within(confirmation).getByRole('button', { name: 'Delete' }))
}

describe('the knowledge-base list', () => {
  it('says which agents use each one, on demand', async () => {
    backend()
    renderApp('/knowledge-base')

    const toggle = await screen.findByRole('button', { name: 'Used by 2 agents' })
    expect(screen.queryByRole('list', { name: 'Agents using Store policies' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Used by 1 agent' })).toBeInTheDocument()

    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const users = screen.getByRole('list', { name: 'Agents using Store policies' })
    expect(within(users).getByRole('link', { name: 'Store analyst' })).toHaveAttribute(
      'href',
      '/agent/a1',
    )
    expect(
      within(users)
        .getAllByRole('link')
        .map((l) => l.textContent),
    ).toEqual(['Store analyst', 'Support desk'])
  })

  it('says when no agent uses one', async () => {
    fakeApi({
      'GET /agents': [],
      'GET /knowledge-bases': [storePolicies],
      'GET /chat-sessions': [],
    })
    renderApp('/knowledge-base')
    expect(await screen.findByText('Not used by any agent')).toBeInTheDocument()
  })

  it('creates one and opens it', async () => {
    const api = backend()
    renderApp('/knowledge-base')

    await userEvent.click(await screen.findByRole('button', { name: 'New knowledge base' }))
    const dialog = await screen.findByRole('dialog', { name: 'New knowledge base' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create knowledge base' }))
    expect(within(dialog).getByText('Enter a name.')).toBeInTheDocument()
    expect(sent(api, 'POST')).toEqual([])

    await userEvent.type(within(dialog).getByLabelText('Name'), ' Onboarding ')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create knowledge base' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Onboarding' })).toBeInTheDocument()
    expect(sent(api, 'POST')[0].body).toEqual({ name: 'Onboarding' })
  })

  it('edits only what changed', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await openMenuItem('Store policies', 'Edit')
    const dialog = await screen.findByRole('dialog', { name: 'Edit Store policies' })
    const description = within(dialog).getByLabelText('Description')
    await userEvent.clear(description)
    await userEvent.type(description, 'Returns only')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(sent(api, 'PATCH')).toEqual([
      { method: 'PATCH', path: '/knowledge-bases/k1', body: { description: 'Returns only' } },
    ])
    expect(await screen.findByText('Returns only')).toBeInTheDocument()
  })
})

describe('deleting a knowledge base', () => {
  it('names its documents and the agents that lose it, then leaves its page', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Store policies?' })
    expect(
      await within(dialog).findByText(
        'This deletes its 2 documents and removes it from these agents:',
      ),
    ).toBeInTheDocument()
    const losing = within(dialog).getByRole('list', { name: 'Agents that lose it' })
    expect(
      within(losing)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['Store analyst', 'Support desk'])

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete knowledge base' }))

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Knowledge bases' }),
    ).toBeInTheDocument()
    expect(sent(api, 'DELETE')).toHaveLength(1)
    // Agents are loaded again: the backend took it out of their knowledge bases (ADR 014).
    await waitFor(() => expect(sent(api, 'GET', '/agents')).toHaveLength(2))
    expect(await screen.findByRole('button', { name: 'Used by 1 agent' })).toBeInTheDocument()
  })

  it('says when no agent uses it', async () => {
    backend({ 'GET /agents': () => [] })
    renderApp('/knowledge-base/k1')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Store policies?' })
    expect(
      await within(dialog).findByText('This deletes its 2 documents. No agent uses it.'),
    ).toBeInTheDocument()
  })

  it('leaves out the documents when it has none', async () => {
    backend()
    renderApp('/knowledge-base/k2')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete Product manuals?' })
    expect(
      await within(dialog).findByText('This removes it from these agents:'),
    ).toBeInTheDocument()
  })
})

describe('a knowledge base page', () => {
  it('shows who uses it and its documents, newest upload first', async () => {
    backend()
    renderApp('/knowledge-base/k1')

    const header = (await screen.findByRole('heading', { level: 1, name: 'Store policies' }))
      .parentElement!
    expect(header).toHaveTextContent('Used by Store analyst and Support desk')
    expect(within(header).getByRole('link', { name: 'Support desk' })).toHaveAttribute(
      'href',
      '/agent/a2',
    )

    const documents = screen.getByRole('region', { name: 'Documents' })
    expect(
      await within(documents).findByRole('heading', { name: '2 documents' }),
    ).toBeInTheDocument()
    expect(
      within(documents)
        .getAllByRole('listitem')
        .map((li) => li.firstChild?.textContent),
    ).toEqual([
      expect.stringContaining('returns-policy.md'),
      expect.stringContaining('opening-hours.md'),
    ])
    expect(within(documents).getByText('Uploaded Nov 2, 2026, 2:05 PM')).toBeInTheDocument()
  })
})

describe('uploading', () => {
  it.each([
    ['notes.txt', 10, 'Not uploaded: only .md or .markdown files.'],
    ['big.md', 1024 * 1024 + 1, 'Not uploaded: larger than 1 MB.'],
  ])('refuses %s before sending it', async (name, size, message) => {
    const api = backend()
    renderApp('/knowledge-base/k1')
    await screen.findByRole('heading', { name: '2 documents' })

    await user().upload(fileInput(), new File([new Uint8Array(size)], name))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(sent(api, 'POST')).toEqual([])
    await userEvent.click(screen.getByRole('button', { name: `Dismiss the error for ${name}` }))
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('shows the file while the server indexes it, then the document', async () => {
    const answer = deferred<void>()
    let created: object | undefined
    const api = backend({
      'POST /documents/upload': async ({ body }) => {
        await answer.promise
        const file = (body as FormData).get('file') as File
        created = doc('d3', file.name, '2026-11-02T04:00:00')
        return created
      },
      'GET /documents/kb/k1': () => [
        ...(created ? [created] : []),
        doc('d2', 'returns-policy.md', '2026-11-02T03:05:00'),
      ],
    })
    renderApp('/knowledge-base/k1')
    await screen.findByRole('heading', { name: '1 document' })

    await user().upload(fileInput(), new File(['# Prices\n'], 'price-list.md'))

    expect(await screen.findByText('Reading its sections and indexing them…')).toBeInTheDocument()
    const call = sent(api, 'POST')[0]
    expect(call.path).toBe('/documents/upload')
    expect((call.body as FormData).get('kbId')).toBe('k1')

    answer.resolve()
    expect(await screen.findByRole('heading', { name: '2 documents' })).toBeInTheDocument()
    expect(screen.queryByText('Reading its sections and indexing them…')).toBeNull()
    expect(screen.getAllByText('price-list.md')).toHaveLength(1)
  })

  it('does not bring the file back once its document is deleted', async () => {
    const priceList = doc('d3', 'price-list.md', '2026-11-02T04:00:00')
    const returnsPolicy = doc('d2', 'returns-policy.md', '2026-11-02T03:05:00')
    let documents = [returnsPolicy]
    const api = backend({
      'GET /documents/kb/k1': () => documents,
      'POST /documents/upload': () => {
        documents = [priceList, returnsPolicy]
        return priceList
      },
      'DELETE /documents/d3': () => {
        documents = [returnsPolicy]
        return null
      },
    })
    renderApp('/knowledge-base/k1')
    await screen.findByRole('heading', { name: '1 document' })
    await user().upload(fileInput(), new File(['# Prices\n'], 'price-list.md'))
    await screen.findByRole('heading', { name: '2 documents' })

    await confirmDocumentDelete('price-list.md')
    // Confirming another delete sends the waiting one at once.
    await confirmDocumentDelete('returns-policy.md')

    await waitFor(() => expect(sent(api, 'DELETE')).toHaveLength(1))
    await waitFor(() => expect(sent(api, 'GET', '/documents/kb/k1')).toHaveLength(3))
    expect(screen.queryByText('Reading its sections and indexing them…')).toBeNull()
    expect(screen.queryByText('price-list.md')).toBeNull()
  })

  it("keeps the backend's refusal on the file's row", async () => {
    backend({
      'POST /documents/upload': () => {
        throw { code: 400, message: 'file: must contain at least one heading' }
      },
    })
    renderApp('/knowledge-base/k1')
    await screen.findByRole('heading', { name: '2 documents' })

    await user().upload(fileInput(), new File(['no heading'], 'catalogue.md'))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Not uploaded: file: must contain at least one heading')
    expect(alert.parentElement).toHaveTextContent('catalogue.md')
  })
})

describe('renaming a document', () => {
  it('saves with Enter in the row', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await openMenuItem('opening-hours.md', 'Rename')
    const input = screen.getByRole('textbox', { name: 'Name of opening-hours.md' })
    await userEvent.clear(input)
    await userEvent.type(input, 'Winter hours.md{Enter}')

    expect(await screen.findByText('Winter hours.md')).toBeInTheDocument()
    expect(sent(api, 'PATCH')).toEqual([
      { method: 'PATCH', path: '/documents/d1', body: { name: 'Winter hours.md' } },
    ])
  })

  it('cancels with Esc and sends nothing for an unchanged or blank name', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await openMenuItem('opening-hours.md', 'Rename')
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Name of opening-hours.md' }),
      'x{Escape}',
    )
    expect(screen.queryByRole('textbox')).toBeNull()

    await openMenuItem('opening-hours.md', 'Rename')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.queryByRole('textbox')).toBeNull()

    await openMenuItem('opening-hours.md', 'Rename')
    await userEvent.clear(screen.getByRole('textbox', { name: 'Name of opening-hours.md' }))
    await userEvent.keyboard('{Enter}')
    expect(screen.getByText('Enter a name.')).toBeInTheDocument()
    expect(sent(api, 'PATCH')).toEqual([])
  })

  it("shows the backend's refusal and keeps the row open", async () => {
    backend({
      'PATCH /documents/d1': () => {
        throw { code: 400, message: 'name: size must be between 0 and 200' }
      },
    })
    renderApp('/knowledge-base/k1')

    await openMenuItem('opening-hours.md', 'Rename')
    await userEvent.type(
      screen.getByRole('textbox', { name: 'Name of opening-hours.md' }),
      'x{Enter}',
    )

    expect(await screen.findByText('name: size must be between 0 and 200')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Name of opening-hours.md' })).toBeInTheDocument()
  })
})

describe('deleting a document', () => {
  async function confirmDelete() {
    await openMenuItem('opening-hours.md', 'Delete')
    const confirmation = await screen.findByRole('tooltip')
    expect(confirmation).toHaveTextContent(
      'Delete opening-hours.md?Agents stop finding its sections.',
    )
    await userEvent.click(within(confirmation).getByRole('button', { name: 'Delete' }))
  }

  it('asks first, then offers Undo, and Undo sends nothing', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await confirmDelete()

    expect(await screen.findByRole('status')).toHaveTextContent('opening-hours.md deleted')
    expect(screen.queryByText('opening-hours.md')).toBeNull()
    expect(screen.getByRole('heading', { name: '1 document' })).toBeInTheDocument()
    expect(sent(api, 'DELETE')).toEqual([])

    await userEvent.click(screen.getByRole('button', { name: 'Undo' }))
    expect(screen.getByText('opening-hours.md')).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()
    expect(sent(api, 'DELETE')).toEqual([])
  })

  it('sends nothing when the confirmation is cancelled', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await openMenuItem('opening-hours.md', 'Delete')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))

    expect(screen.getByText('opening-hours.md')).toBeInTheDocument()
    expect(screen.queryByRole('status')).toBeNull()
    expect(sent(api, 'DELETE')).toEqual([])
  })

  it('sends the delete when leaving the page before the wait ends', async () => {
    const api = backend()
    renderApp('/knowledge-base/k1')

    await confirmDelete()
    await userEvent.click(screen.getByRole('link', { name: 'Product manuals' }))

    expect(sent(api, 'DELETE')).toEqual([
      { method: 'DELETE', path: '/documents/d1', body: undefined },
    ])
  })
})
