import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useReturnFocus } from './useReturnFocus'

function button(name: string) {
  const element = document.createElement('button')
  element.textContent = name
  document.body.append(element)
  return element
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  document.body.replaceChildren()
})

/** Opens the "dialog" from `opener`, then closes it after `whileOpen` ran. */
function openAndClose(opener: HTMLElement, whileOpen: () => void) {
  const { result, rerender } = renderHook(({ open }) => useReturnFocus(open), {
    initialProps: { open: false },
  })
  opener.focus()
  act(() => result.current.remember())
  rerender({ open: true })
  whileOpen()
  rerender({ open: false })
  act(() => {
    vi.runAllTimers()
  })
}

describe('useReturnFocus', () => {
  it('puts focus back on the opener when the closed dialog left it on the page', () => {
    const opener = button('New agent')

    openAndClose(opener, () => (document.activeElement as HTMLElement).blur())

    expect(opener).toHaveFocus()
  })

  it('leaves focus where a new page put it', () => {
    const opener = button('New agent')
    const heading = button('Trip planner')

    openAndClose(opener, () => heading.focus())

    expect(heading).toHaveFocus()
  })

  it('does nothing when the opener has left the page', () => {
    const opener = button('More actions')

    openAndClose(opener, () => opener.remove())

    expect(document.body).toHaveFocus()
  })
})
