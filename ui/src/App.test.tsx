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

describe('moving between pages', () => {
  const entering = () => document.querySelector('main [data-entering]')

  it('moves nothing on the first page, then fades in each page the user opens', async () => {
    renderAt('/agent')
    await screen.findByRole('heading', { level: 1, name: 'Agents' })
    expect(entering()).toBeNull()

    await userEvent.click(screen.getByRole('link', { name: 'Knowledge bases' }))
    await screen.findByRole('heading', { level: 1, name: 'Knowledge bases' })
    expect(entering()).not.toBeNull()
  })

  it('puts every page in the centred column', async () => {
    renderAt('/agent')
    const heading = await screen.findByRole('heading', { level: 1, name: 'Agents' })
    expect(heading.parentElement).toHaveClass('mx-auto', 'max-w-230')
  })
})

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
    expect(screen.getByRole('link', { name: 'Start a chat' })).toHaveAttribute('href', '/chat/new')
  })

  it('always shows the full product name: in the list panel, and in the top bar of a narrow screen', () => {
    renderAt('/')

    expect(screen.getAllByText('ThinkXecAI')).toHaveLength(2)
  })
})

describe('the shell (ADR 063)', () => {
  const panel = () => document.querySelector('aside')!

  it('is as tall as the window and lets the list and the page scroll on their own', () => {
    const { container } = renderAt('/chat')

    expect(container.firstElementChild).toHaveClass('h-dvh', 'overflow-hidden')
    expect(panel()).toHaveClass('overflow-y-auto')
    expect(screen.getByRole('main')).toHaveClass('overflow-y-auto')
  })

  it.each(['/agent', '/chat', '/knowledge-base'])(
    'shows the list alone at %s on a narrow screen, and both on a wide one',
    (path) => {
      renderAt(path)

      expect(panel()).toHaveClass('flex', 'wide:flex')
      expect(screen.getByRole('main')).toHaveClass('hidden', 'wide:flex')
    },
  )

  it.each(['/', '/chat/new', '/chat/s1', '/agent/a1', '/knowledge-base/k1', '/settings'])(
    'shows the page alone at %s on a narrow screen, and both on a wide one',
    (path) => {
      renderAt(path)

      expect(panel()).toHaveClass('hidden', 'wide:flex')
      expect(screen.getByRole('main')).toHaveClass('flex', 'wide:flex')
    },
  )

  it('opens a new chat at /chat/new', async () => {
    renderAt('/chat/new')

    expect(await screen.findByRole('heading', { level: 1, name: 'New chat' })).toBeInTheDocument()
  })

  it('leads a new chat back to the list of chats', async () => {
    renderAt('/chat/new')

    expect(await screen.findByRole('link', { name: 'Back to chats' })).toHaveAttribute(
      'href',
      '/chat',
    )
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

    // Navigation waits for the next page's lazy code, so the section changes a moment after the click.
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Knowledge bases' }),
    ).toBeInTheDocument()
    expect(currentSection()).toEqual(['Knowledge bases'])
  })
})
