import { render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import Content from './Content'

/** A page change whose heading arrives later, as a page still loading. */
function renderLateHeading() {
  const page = (number: number, body: ReactNode) => (
    <>
      <button type="button">Search chats</button>
      <Content page={number} hiddenWhenNarrow={false}>
        {body}
      </Content>
    </>
  )
  const view = render(page(0, <h1>First</h1>))
  view.rerender(page(1, <p>Loading…</p>))
  return { showHeading: () => view.rerender(page(1, <h1>Second</h1>)) }
}

describe('focus after a page change (Phase 14, D6)', () => {
  it('moves focus to a heading that arrives later', async () => {
    const { showHeading } = renderLateHeading()

    showHeading()

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Second' })).toHaveFocus())
  })

  it('leaves focus where the user moved it before the heading arrived', async () => {
    const { showHeading } = renderLateHeading()
    const search = screen.getByRole('button', { name: 'Search chats' })
    search.focus()

    showHeading()

    // The observer has run once the heading is in the page; give it a tick more.
    await screen.findByRole('heading', { name: 'Second' })
    await new Promise((resolve) => setTimeout(resolve))
    expect(search).toHaveFocus()
  })
})
