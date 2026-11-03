import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { UNDO_MS, useUndoableDelete } from './useUndoableDelete'

const returns = { id: 'd1', name: 'returns-policy.md' }
const hours = { id: 'd2', name: 'opening-hours.md' }

beforeEach(() => {
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
})

function setup(remove = vi.fn(async () => {})) {
  const hook = renderHook(() => useUndoableDelete(remove))
  return { ...hook, remove }
}

describe('useUndoableDelete', () => {
  it('hides the item at once and sends the delete only when the wait ends', () => {
    const { result, remove } = setup()

    act(() => result.current.confirm(returns))
    expect(result.current.hidden.has('d1')).toBe(true)
    expect(result.current.pending).toEqual(returns)

    act(() => vi.advanceTimersByTime(UNDO_MS - 1))
    expect(remove).not.toHaveBeenCalled()

    act(() => vi.advanceTimersByTime(1))
    expect(remove).toHaveBeenCalledExactlyOnceWith('d1', undefined)
    expect(result.current.pending).toBeNull()
    expect(result.current.hidden.has('d1')).toBe(true)
  })

  it('sends nothing after Undo and shows the item again', () => {
    const { result, remove } = setup()

    act(() => result.current.confirm(returns))
    act(() => result.current.undo())
    act(() => vi.advanceTimersByTime(UNDO_MS * 2))

    expect(remove).not.toHaveBeenCalled()
    expect(result.current.pending).toBeNull()
    expect(result.current.hidden.has('d1')).toBe(false)
  })

  it('sends a waiting delete at once when another one is confirmed', () => {
    const { result, remove } = setup()

    act(() => result.current.confirm(returns))
    act(() => result.current.confirm(hours))

    expect(remove).toHaveBeenCalledExactlyOnceWith('d1', undefined)
    expect(result.current.pending).toEqual(hours)
    act(() => vi.advanceTimersByTime(UNDO_MS))
    expect(remove).toHaveBeenLastCalledWith('d2', undefined)
    expect(remove).toHaveBeenCalledTimes(2)
  })

  it('sends a waiting delete with keepalive when the tab is closed', () => {
    const { result, remove } = setup()

    act(() => result.current.confirm(returns))
    act(() => {
      window.dispatchEvent(new Event('pagehide'))
    })

    expect(remove).toHaveBeenCalledExactlyOnceWith('d1', { keepalive: true })
    act(() => vi.advanceTimersByTime(UNDO_MS))
    expect(remove).toHaveBeenCalledTimes(1)
  })

  it('sends a waiting delete when the page goes away', () => {
    const { result, remove, unmount } = setup()

    act(() => result.current.confirm(returns))
    unmount()

    expect(remove).toHaveBeenCalledExactlyOnceWith('d1', { keepalive: true })
  })

  it('sends nothing on leaving when nothing is waiting', () => {
    const { remove, unmount } = setup()
    window.dispatchEvent(new Event('pagehide'))
    unmount()
    expect(remove).not.toHaveBeenCalled()
  })

  it('shows the item again and reports a failed delete', async () => {
    const failure = new Error('gone')
    const { result } = setup(vi.fn(async () => Promise.reject(failure)))

    act(() => result.current.confirm(returns))
    await act(async () => vi.advanceTimersByTimeAsync(UNDO_MS))

    expect(result.current.hidden.has('d1')).toBe(false)
    expect(result.current.failure).toEqual({ name: 'returns-policy.md', error: failure })
    act(() => result.current.dismissFailure())
    expect(result.current.failure).toBeNull()
  })
})
