import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from './App'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  )
}

function currentSection() {
  const nav = screen.getByRole('navigation', { name: 'Main' })
  return Array.from(nav.querySelectorAll('[aria-current="page"]'), (link) =>
    link.getAttribute('aria-label'),
  )
}

describe('routes', () => {
  it.each([
    ['/', 'New chat', 'Chats'],
    ['/agent', 'New chat', 'Agents'],
    ['/chat', 'New chat', 'Chats'],
    ['/chat/5b0c', 'Chat', 'Chats'],
    ['/knowledge-base', 'Knowledge bases', 'Knowledge bases'],
    ['/knowledge-base/7a1e', 'Knowledge base', 'Knowledge bases'],
  ])('%s shows %j with %s open in the side menu', (path, heading, section) => {
    renderAt(path)

    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
    expect(currentSection()).toEqual([section])
    expect(screen.getByRole('region', { name: section })).toBeInTheDocument()
  })

  it('shows a not-found page for an unknown path, with no section open', () => {
    renderAt('/settings')

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
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
    expect(screen.getByRole('heading', { level: 1, name: 'Knowledge bases' })).toBeInTheDocument()
  })
})
