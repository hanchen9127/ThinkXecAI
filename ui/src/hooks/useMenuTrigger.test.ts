import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useMenuTrigger } from './useMenuTrigger'

describe('useMenuTrigger', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('runs the action a tick later', () => {
    const action = vi.fn()
    const { result } = renderHook(() => useMenuTrigger())

    result.current.then(action)()
    expect(action).not.toHaveBeenCalled()
    vi.runAllTimers()

    expect(action).toHaveBeenCalledOnce()
  })

  it('runs only the last action chosen within one tick', () => {
    const first = vi.fn()
    const second = vi.fn()
    const { result } = renderHook(() => useMenuTrigger())

    result.current.then(first)()
    result.current.then(second)()
    vi.runAllTimers()

    expect(first).not.toHaveBeenCalled()
    expect(second).toHaveBeenCalledOnce()
  })

  it('drops the action when the row unmounts within that tick', () => {
    const action = vi.fn()
    const { result, unmount } = renderHook(() => useMenuTrigger())

    result.current.then(action)()
    unmount()
    vi.runAllTimers()

    expect(action).not.toHaveBeenCalled()
  })
})
