import { useEffect, useMemo, useState } from 'react'
import type { ChatMessage } from '../api/api'
import { parseApiTimestamp } from '../utils/time'

/**
 * One model call can take 3 × 120 s + 8 s (ADR 039); after 7 minutes without a new message a run
 * is taken to have died without a notice (a backend restart), D4.
 */
export const WORKING_TIMEOUT_MS = 7 * 60 * 1000

/**
 * Whether the agent still owes a reply after `last` (D4): a user message, an assistant message that
 * asked for tools, or a tool result. An answer or a notice (assistant without tool calls) ends it.
 */
export function awaitsAgent(last: ChatMessage | undefined): boolean {
  if (!last) return false
  if (last.role === 'user' || last.role === 'tool') return true
  return last.role === 'assistant' && (last.metadata?.toolCalls?.length ?? 0) > 0
}

/**
 * Whether to show that the agent is working (ADR 055): inferred from the newest message, and off
 * once that message is 7 minutes old, counted from its own time, so a run that died long ago shows
 * nothing when its chat is opened. Display only: the input stays enabled (ADR 042).
 */
export function useWorking(messages: ChatMessage[]): boolean {
  const last = useMemo(() => {
    let newest: ChatMessage | undefined
    for (const message of messages) {
      if (!newest || time(message) >= time(newest)) newest = message
    }
    return newest
  }, [messages])
  const [expired, setExpired] = useState<string>()

  const waiting = awaitsAgent(last)
  const id = last?.id
  const deadline = last ? time(last) + WORKING_TIMEOUT_MS : 0
  useEffect(() => {
    if (!waiting || id === undefined) return
    const timer = setTimeout(() => setExpired(id), Math.max(0, deadline - Date.now()))
    return () => clearTimeout(timer)
  }, [waiting, id, deadline])

  return waiting && expired !== id
}

function time(message: ChatMessage) {
  return parseApiTimestamp(message.createdAt).getTime()
}
