import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ChatMessage } from '../api/api'
import { WORKING_TIMEOUT_MS, awaitsAgent, useWorking } from './useWorking'

const NOW = new Date('2026-11-02T03:00:00Z')

/** A message created `ago` ms before NOW. */
function message(
  id: string,
  role: ChatMessage['role'],
  ago = 0,
  metadata: ChatMessage['metadata'] = null,
): ChatMessage {
  const at = new Date(NOW.getTime() - ago).toISOString().slice(0, -1)
  return { id, sessionId: 's1', role, content: 'x', metadata, createdAt: at, updatedAt: at }
}
const withTools = { toolCalls: [{ id: 'c1', type: 'function', name: 'dateTool', arguments: '{}' }] }
const result = { toolResponse: { id: 'c1', name: 'dateTool', responseData: '"2026-11-02"' } }

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(NOW)
})
afterEach(() => {
  vi.useRealTimers()
})

function track(initial: ChatMessage[]) {
  return renderHook(({ messages }) => useWorking(messages), { initialProps: { messages: initial } })
}

describe('awaitsAgent', () => {
  it('waits after a user message, a call for tools, and a tool result', () => {
    expect(awaitsAgent(message('m1', 'user'))).toBe(true)
    expect(awaitsAgent(message('m1', 'assistant', 0, withTools))).toBe(true)
    expect(awaitsAgent(message('m1', 'tool', 0, result))).toBe(true)
  })

  it('stops after an answer or a notice, and for an empty chat', () => {
    expect(awaitsAgent(message('m1', 'assistant'))).toBe(false)
    expect(awaitsAgent(message('m1', 'assistant', 0, { toolCalls: [] }))).toBe(false)
    expect(awaitsAgent(message('m1', 'system'))).toBe(false)
    expect(awaitsAgent(undefined)).toBe(false)
  })
})

describe('useWorking', () => {
  it('is on from a sent message through tool calls, and off at the answer', () => {
    const question = message('m1', 'user')
    const { result: working, rerender } = track([question])
    expect(working.current).toBe(true)

    const call = message('m2', 'assistant', -1000, withTools)
    rerender({ messages: [question, call] })
    expect(working.current).toBe(true)

    const toolResult = message('m3', 'tool', -2000, result)
    rerender({ messages: [question, call, toolResult] })
    expect(working.current).toBe(true)

    rerender({ messages: [question, call, toolResult, message('m4', 'assistant', -3000)] })
    expect(working.current).toBe(false)
  })

  it('goes by the newest message, whatever the order it arrived in', () => {
    const { result: working } = track([message('m2', 'assistant', 0), message('m1', 'user', 5000)])
    expect(working.current).toBe(false)
  })

  it('turns off 7 minutes after the last message', () => {
    const { result: working } = track([message('m1', 'user')])

    act(() => vi.advanceTimersByTime(WORKING_TIMEOUT_MS - 1))
    expect(working.current).toBe(true)
    act(() => vi.advanceTimersByTime(1))
    expect(working.current).toBe(false)
  })

  it('counts the 7 minutes again from each new message', () => {
    const question = message('m1', 'user')
    const { result: working, rerender } = track([question])

    act(() => vi.advanceTimersByTime(WORKING_TIMEOUT_MS - 1000))
    const call = message('m2', 'assistant', -(WORKING_TIMEOUT_MS - 1000), withTools)
    rerender({ messages: [question, call] })
    act(() => vi.advanceTimersByTime(WORKING_TIMEOUT_MS - 1))
    expect(working.current).toBe(true)
    act(() => vi.advanceTimersByTime(1))
    expect(working.current).toBe(false)
  })

  it('counts from the message time when a chat opens: a run that died long ago shows nothing', () => {
    const stale = track([message('m1', 'tool', 2 * 60 * 60 * 1000, result)])
    act(() => vi.advanceTimersByTime(0))
    expect(stale.result.current).toBe(false)

    const recent = track([message('m1', 'tool', 3 * 60 * 1000, result)])
    act(() => vi.advanceTimersByTime(4 * 60 * 1000 - 1))
    expect(recent.result.current).toBe(true)
    act(() => vi.advanceTimersByTime(1))
    expect(recent.result.current).toBe(false)
  })
})
