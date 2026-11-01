import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { deferred } from '../test/fakeApi'
import { useRemoteData } from './useRemoteData'

describe('useRemoteData', () => {
  it('loads, then shows the data', async () => {
    const load = () => Promise.resolve(['a'])
    const { result } = renderHook(() => useRemoteData(load))

    expect(result.current).toMatchObject({ loading: true, data: undefined })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current).toMatchObject({ data: ['a'], error: undefined })
  })

  it('shows the error of a failed load', async () => {
    const failure = new Error('down')
    const load = () => Promise.reject(failure)
    const { result } = renderHook(() => useRemoteData(load))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current).toMatchObject({ data: undefined, error: failure })
  })

  it('loads nothing for null', () => {
    const { result } = renderHook(() => useRemoteData(null))

    expect(result.current).toMatchObject({ loading: false, data: undefined, error: undefined })
  })

  it('keeps the data while it reloads, then shows the new data', async () => {
    let answer = 'first'
    const second = deferred<string>()
    const load = vi.fn(() => (answer === 'first' ? Promise.resolve(answer) : second.promise))
    const { result } = renderHook(() => useRemoteData(load))
    await waitFor(() => expect(result.current.data).toBe('first'))

    answer = 'second'
    act(() => result.current.reload())

    expect(result.current).toMatchObject({ loading: true, data: 'first' })
    await act(async () => second.resolve('second'))
    expect(result.current).toMatchObject({ loading: false, data: 'second' })
  })

  it('clears a failed reload error once a later reload succeeds', async () => {
    let fail = true
    const load = () => (fail ? Promise.reject(new Error('down')) : Promise.resolve('ok'))
    const { result } = renderHook(() => useRemoteData(load))
    await waitFor(() => expect(result.current.error).toBeInstanceOf(Error))

    fail = false
    act(() => result.current.reload())

    await waitFor(() => expect(result.current.data).toBe('ok'))
    expect(result.current.error).toBeUndefined()
  })

  it('hides the previous error while it loads again', async () => {
    const retry = deferred<string>()
    let fail = true
    const load = () => (fail ? Promise.reject(new Error('down')) : retry.promise)
    const { result } = renderHook(() => useRemoteData(load))
    await waitFor(() => expect(result.current.error).toBeInstanceOf(Error))

    fail = false
    act(() => result.current.reload())

    expect(result.current).toMatchObject({ loading: true, error: undefined })
  })

  it('ignores the answer for an earlier input that arrives after the current one', async () => {
    const slow = deferred<string>()
    const loads: Record<string, () => Promise<string>> = {
      kb1: () => slow.promise,
      kb2: () => Promise.resolve('documents of kb2'),
    }
    const { result, rerender } = renderHook(({ kb }) => useRemoteData(loads[kb]), {
      initialProps: { kb: 'kb1' },
    })

    rerender({ kb: 'kb2' })
    await waitFor(() => expect(result.current.data).toBe('documents of kb2'))
    await act(async () => slow.resolve('documents of kb1'))

    expect(result.current.data).toBe('documents of kb2')
  })

  it('shows no data of the previous input while the new one loads', async () => {
    const pending = deferred<string>()
    const loads: Record<string, () => Promise<string>> = {
      kb1: () => Promise.resolve('documents of kb1'),
      kb2: () => pending.promise,
    }
    const { result, rerender } = renderHook(({ kb }) => useRemoteData(loads[kb]), {
      initialProps: { kb: 'kb1' },
    })
    await waitFor(() => expect(result.current.data).toBe('documents of kb1'))

    rerender({ kb: 'kb2' })

    expect(result.current).toMatchObject({ loading: true, data: undefined })
  })
})
