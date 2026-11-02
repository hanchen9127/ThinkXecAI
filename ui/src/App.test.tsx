import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { fakeApi } from './test/fakeApi'
import { renderApp } from './test/renderApp'

function renderAt(path: string) {
  return renderApp(path)
}

beforeEach(() => {
  fakeApi({
    'GET /agents': [],
    'GET /knowledge-bases': [],
    'GET /chat-sessions': [],
  })
})

function currentSection() {
  const nav = screen.getByRole('navigation', { name: 'Main' })
  return Array.from(nav.querySelectorAll('[aria-current="page"]'), (link) =>
    link.getAttribute('aria-label'),
  )
}

describe('routes', () => {
  it.each([
    ['/', 'New chat', 'Chats'],
    ['/agent', 'Agents', 'Agents'],
    ['/chat', 'New chat', 'Chats'],
    ['/chat/5b0c', 'Chat not found', 'Chats'],
    ['/knowledge-base', 'Knowledge bases', 'Knowledge bases'],
    ['/knowledge-base/7a1e', 'Knowledge base not found', 'Knowledge bases'],
  ])('%s shows %j with %s open in the side menu', async (path, heading, section) => {
    renderAt(path)

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
    expect(currentSection()).toEqual([section])
    expect(screen.getByRole('region', { name: section })).toBeInTheDocument()
  })

  it('shows a not-found page for an unknown path, with no section open', async () => {
    renderAt('/settings')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Page not found' }),
    ).toBeInTheDocument()
    expect(currentSection()).toEqual([])
    expect(screen.getByRole('link', { name: 'Go to chats' })).toHaveAttribute('href', '/chat')
  })

  it('always shows the full product name', () => {
    renderAt('/')

    expect(screen.getByText('Xec').closest('p')).toHaveTextContent(/^ThinkXecAI$/)
  })
})

describe('side menu', () => {
  it('links each section to its route', () => {
    renderAt('/')

    expect(screen.getByRole('link', { name: 'Agents' })).toHaveAttribute('href', '/agent')
    expect(screen.getByRole('link', { name: 'Chats' })).toHaveAttribute('href', '/chat')
    expect(screen.getByRole('link', { name: 'Knowledge bases' })).toHaveAttribute(
      'href',
      '/knowledge-base',
    )
  })

  it('opens the section that is clicked', async () => {
    renderAt('/chat')

    await userEvent.click(screen.getByRole('link', { name: 'Knowledge bases' }))

    expect(currentSection()).toEqual(['Knowledge bases'])
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Knowledge bases' }),
    ).toBeInTheDocument()
  })
})
