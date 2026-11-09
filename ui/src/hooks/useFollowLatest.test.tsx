import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ScrollAreaContext } from '../layout/scrollArea'
import { useFollowLatest } from './useFollowLatest'

/** A 2,000 px page in a 500 px scroll area, `main` (ADR 063); its end is at 1,500. */
let area: HTMLElement
let top: number
/** Every position the hook moved the page to. */
let moves: number[]

function fakeArea() {
  const element = document.createElement('main')
  Object.defineProperty(element, 'scrollHeight', { get: () => 2000 })
  Object.defineProperty(element, 'clientHeight', { get: () => 500 })
  Object.defineProperty(element, 'scrollTop', { get: () => top })
  element.scrollTo = ((options: ScrollToOptions) => {
    moves.push(options.top ?? 0)
  }) as typeof element.scrollTo
  return element
}

function wrapper({ children }: { children: ReactNode }) {
  return <ScrollAreaContext value={area}>{children}</ScrollAreaContext>
}

function start() {
  const view = renderHook(({ latest }) => useFollowLatest(latest), {
    initialProps: { latest: 0 },
    wrapper,
  })
  moves = []
  return view
}

/** The reader scrolls the area to `y`. */
function scrollArea(y: number) {
  top = y
  act(() => {
    area.dispatchEvent(new Event('scroll'))
  })
}

beforeEach(() => {
  top = 0
  moves = []
  area = fakeArea()
})

afterEach(() => {
  top = 0
})

describe('useFollowLatest', () => {
  it('opens at the end', () => {
    renderHook(() => useFollowLatest(0), { wrapper })
    expect(moves).toEqual([2000])
  })

  it('goes to the end when something new arrives', () => {
    const { rerender } = start()
    rerender({ latest: 1 })
    expect(moves).toEqual([2000])
  })

  it('does not move the page when nothing new arrived', () => {
    const { rerender } = start()
    rerender({ latest: 0 })
    expect(moves).toEqual([])
  })

  it('leaves a reader who scrolled up where they are', () => {
    const { rerender } = start()
    scrollArea(1500)
    scrollArea(900)
    rerender({ latest: 1 })
    expect(moves).toEqual([])
  })

  it('keeps following when the page grew under a scroll event that is not upwards', () => {
    const { rerender } = start()
    scrollArea(30)
    rerender({ latest: 1 })
    expect(moves).toEqual([2000])
  })

  it('counts a reader just short of the end as being there', () => {
    const { rerender } = start()
    scrollArea(1500)
    scrollArea(900)
    scrollArea(1453)
    rerender({ latest: 1 })
    expect(moves).toEqual([2000])
  })

  it('does not count a reader further up as being there', () => {
    const { rerender } = start()
    scrollArea(1500)
    scrollArea(1452)
    rerender({ latest: 1 })
    expect(moves).toEqual([])
  })

  it('follows again after `follow`', () => {
    const { result, rerender } = start()
    scrollArea(1500)
    scrollArea(900)
    result.current()
    rerender({ latest: 1 })
    expect(moves).toEqual([2000])
  })

  it('ignores the window: only the area it scrolls in counts', () => {
    const { rerender } = start()
    scrollArea(1500)
    scrollArea(900)
    act(() => {
      window.dispatchEvent(new Event('scroll'))
    })
    rerender({ latest: 1 })
    expect(moves).toEqual([])
  })

  it('does nothing without a scroll area', () => {
    const { rerender } = renderHook(({ latest }) => useFollowLatest(latest), {
      initialProps: { latest: 0 },
    })
    rerender({ latest: 1 })
    expect(moves).toEqual([])
  })
})
