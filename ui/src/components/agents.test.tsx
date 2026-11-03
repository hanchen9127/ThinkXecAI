import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { fakeApi } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

const storeAnalyst = {
  id: 'a1',
  name: 'Store analyst',
  description: 'Answers questions about orders and stock.',
  model: 'deepseek-chat',
  allowedTools: ['dataBaseTool', 'emailTool'],
  allowedKbs: ['k1'],
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: 'You are a careful analyst.\nPrefer data over guesses.',
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-02T03:10:00',
}
const tripPlanner = {
  ...storeAnalyst,
  id: 'a2',
  name: 'Trip planner',
  description: null,
  model: 'qwen3.8-flash',
  allowedTools: [],
  allowedKbs: [],
  systemPrompt: null,
}

function backend(agents: object[] = [storeAnalyst, tripPlanner]) {
  return fakeApi({
    'GET /agents': agents,
    'GET /knowledge-bases': [{ id: 'k1', name: 'Store policies' }],
    'GET /chat-sessions': [],
  })
}

function agentList() {
  return screen.getByRole('region', { name: 'Agents' })
}

describe('agent list', () => {
  it('summarises each agent as model and counts', async () => {
    backend()
    renderApp('/agent')

    const list = agentList()
    expect(await within(list).findByRole('link', { name: 'Store analyst' })).toHaveAttribute(
      'href',
      '/agent/a1',
    )
    expect(within(list).getByRole('button', { name: '2 tools' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
    expect(within(list).getByRole('button', { name: '1 knowledge base' })).toBeInTheDocument()
    expect(within(list).getByText('no tools')).not.toHaveAttribute('aria-expanded')
    expect(within(list).queryByRole('button', { name: 'no tools' })).toBeNull()
  })

  it('opens a count into names and folds it again', async () => {
    backend()
    renderApp('/agent')
    const tools = await within(agentList()).findByRole('button', { name: '2 tools' })

    await userEvent.click(tools)

    expect(tools).toHaveAttribute('aria-expanded', 'true')
    const names = screen.getByRole('list', { name: 'Tools of Store analyst' })
    expect(
      within(names)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['dataBaseTool', 'emailTool'])

    await userEvent.click(tools)

    expect(screen.queryByRole('list', { name: 'Tools of Store analyst' })).toBeNull()
  })

  it('names the knowledge bases, not their ids', async () => {
    backend()
    renderApp('/agent')

    await userEvent.click(
      await within(agentList()).findByRole('button', { name: '1 knowledge base' }),
    )

    expect(
      screen.getByRole('list', { name: 'Knowledge bases of Store analyst' }),
    ).toHaveTextContent('Store policies')
  })

  it('names a knowledge base missing from the list as unknown', async () => {
    backend([{ ...storeAnalyst, allowedKbs: ['k1', 'gone'] }])
    renderApp('/agent')

    await userEvent.click(
      await within(agentList()).findByRole('button', { name: '2 knowledge bases' }),
    )

    const names = screen.getByRole('list', { name: 'Knowledge bases of Store analyst' })
    expect(
      within(names)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['Store policies', 'Unknown knowledge base'])
  })

  it('marks the open agent', async () => {
    backend()
    renderApp('/agent/a2')

    expect(await within(agentList()).findByRole('link', { name: 'Trip planner' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(within(agentList()).getByRole('link', { name: 'Store analyst' })).not.toHaveAttribute(
      'aria-current',
    )
  })

  it('says so when there are no agents', async () => {
    backend([])
    renderApp('/agent')

    expect(await within(agentList()).findByText('No agents yet.')).toBeInTheDocument()
  })

  it('shows why the list failed and loads it again on request', async () => {
    let fail = true
    fakeApi({
      'GET /agents': () => {
        if (fail) throw { code: 500, message: 'Internal server error' }
        return [storeAnalyst]
      },
      'GET /knowledge-bases': [],
      'GET /chat-sessions': [],
    })
    renderApp('/agent')

    expect(await within(agentList()).findByRole('alert')).toHaveTextContent(
      "Couldn't load agents. Internal server error",
    )
    fail = false
    await userEvent.click(within(agentList()).getByRole('button', { name: 'Try again' }))

    expect(
      await within(agentList()).findByRole('link', { name: 'Store analyst' }),
    ).toBeInTheDocument()
  })
})

describe('agent profile', () => {
  it('shows how the agent is set up', async () => {
    backend()
    renderApp('/agent/a1')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Store analyst' }),
    ).toBeInTheDocument()
    const setup = screen.getByRole('region', { name: 'Setup' })
    const row = (term: string) => within(setup).getByText(term).nextElementSibling
    expect(row('Model')).toHaveTextContent('deepseek-chat')
    expect(row('Tools')).toHaveTextContent(
      'dataBaseToolemailToolplus knowledge search, date, city, weather',
    )
    expect(row('Knowledge bases')).toHaveTextContent('Store policies')
    expect(row('Remembered messages')).toHaveTextContent('The last 10')
    expect(row('Temperature, top P')).toHaveTextContent('0.7, 1.00saved, not yet used at run time')
    expect(row('Instructions')).toHaveTextContent(
      'You are a careful analyst. Prefer data over guesses.',
    )
    expect(within(setup).getByText(/^Updated /)).toHaveTextContent('Updated Nov 2, 2026')
  })

  it('says None for no knowledge bases and no instructions', async () => {
    backend()
    renderApp('/agent/a2')

    const setup = await screen.findByRole('region', { name: 'Setup' })
    expect(within(setup).getByText('Knowledge bases').nextElementSibling).toHaveTextContent('None')
    expect(within(setup).getByText('Instructions').nextElementSibling).toHaveTextContent('None')
    expect(within(setup).getByText('Tools').nextElementSibling).toHaveTextContent(
      /^knowledge search, date, city, weather$/,
    )
  })

  it('starts a chat with this agent chosen', async () => {
    backend()
    renderApp('/agent/a1')

    expect(await screen.findByRole('link', { name: 'Start chat' })).toHaveAttribute(
      'href',
      '/chat?agent=a1',
    )
  })

  it('says so when the agent does not exist', async () => {
    backend()
    renderApp('/agent/gone')

    expect(await screen.findByRole('heading', { name: 'Agent not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to agents' })).toHaveAttribute('href', '/agent')
  })

  it('opens when its name is clicked in the list', async () => {
    backend()
    renderApp('/agent')

    await userEvent.click(await within(agentList()).findByRole('link', { name: 'Trip planner' }))

    expect(screen.getByRole('heading', { level: 1, name: 'Trip planner' })).toBeInTheDocument()
  })
})
