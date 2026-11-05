import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useFollowLatest } from './useFollowLatest'

const scrollTo = vi.fn()

function start() {
  const view = renderHook(({ latest }) => useFollowLatest(latest), { initialProps: { latest: 0 } })
  scrollTo.mockClear()
  return view
}

/** Scrolls a 2,000 px page in a 500 px window to `y`; its end is at 1,500. */
function scrollPage(y: number) {
  window.scrollY = y
  act(() => {
    window.dispatchEvent(new Event('scroll'))
  })
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', scrollTo)
  vi.spyOn(document.documentElement, 'scrollHeight', 'get').mockReturnValue(2000)
  vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(500)
  window.scrollY = 0
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  window.scrollY = 0
  scrollTo.mockClear()
})

describe('useFollowLatest', () => {
  it('opens at the end', () => {
    renderHook(() => useFollowLatest(0))
    expect(scrollTo).toHaveBeenCalledWith(0, 2000)
  })

  it('goes to the end when something new arrives', () => {
    const { rerender } = start()
    rerender({ latest: 1 })
    expect(scrollTo).toHaveBeenCalledWith(0, 2000)
  })

  it('does not move the page when nothing new arrived', () => {
    const { rerender } = start()
    rerender({ latest: 0 })
    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('leaves a reader who scrolled up where they are', () => {
    const { rerender } = start()
    scrollPage(1500)
    scrollPage(900)
    rerender({ latest: 1 })
    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('keeps following when the page grew under a scroll event that is not upwards', () => {
    const { rerender } = start()
    scrollPage(30)
    rerender({ latest: 1 })
    expect(scrollTo).toHaveBeenCalledTimes(1)
  })

  it('counts a reader just short of the end as being there', () => {
    const { rerender } = start()
    scrollPage(1500)
    scrollPage(900)
    scrollPage(1453)
    rerender({ latest: 1 })
    expect(scrollTo).toHaveBeenCalledTimes(1)
  })

  it('does not count a reader further up as being there', () => {
    const { rerender } = start()
    scrollPage(1500)
    scrollPage(1452)
    rerender({ latest: 1 })
    expect(scrollTo).not.toHaveBeenCalled()
  })

  it('follows again after `follow`', () => {
    const { result, rerender } = start()
    scrollPage(1500)
    scrollPage(900)
    result.current()
    rerender({ latest: 1 })
    expect(scrollTo).toHaveBeenCalledTimes(1)
  })
})
