import { useEffect, useMemo, useState } from 'react'
import type { ChatMessage } from '../api/api'
import { parseApiTimestamp } from '../utils/time'

/**
 * One model call can take 3 × 120 s + 8 s (ADR 039); after 7 minutes without a new message a run
 * is taken to have died without a notice (a backend restart), D4.
 */
export const WORKING_TIMEOUT_MS = 7 * 60 * 1000

/** The tool that ends a run when called in a reply with text (ADR 045). */
const TERMINATE = 'terminate'

/**
 * Whether the agent still owes a reply after `last`, the newest of `messages` (D4): a user message,
 * an assistant message that asked for tools, or a tool result. An answer or a notice (assistant
 * without tool calls) ends it, and so do the results of a reply with text that called `terminate`:
 * the run ends there (ADR 045). `terminate` in a reply without text is refused and the run goes on.
 */
export function awaitsAgent(last: ChatMessage | undefined, messages: ChatMessage[]): boolean {
  if (!last) return false
  if (last.role === 'user') return true
  if (last.role === 'tool') return !endsRun(callerOf(last, messages))
  return last.role === 'assistant' && (last.metadata?.toolCalls?.length ?? 0) > 0
}

/** The newest reply holding the answered call; ids come from the model, so an older run may reuse one. */
function callerOf(result: ChatMessage, messages: ChatMessage[]) {
  const id = result.metadata?.toolResponse?.id
  let caller: ChatMessage | undefined
  for (const message of messages) {
    if (!message.metadata?.toolCalls?.some((call) => call.id === id)) continue
    if (!caller || time(message) >= time(caller)) caller = message
  }
  return caller
}

function endsRun(reply: ChatMessage | undefined) {
  return (
    !!reply?.content?.trim() && !!reply.metadata?.toolCalls?.some((call) => call.name === TERMINATE)
  )
}

/**
 * Whether to show that the agent is working (ADR 055): inferred from the newest message, and off
 * once that message is 7 minutes old, counted from its own time, so a run that died long ago shows
 * nothing when its chat is opened. Display only: the input stays enabled (ADR 042).
 */
export function useWorking(messages: ChatMessage[]): boolean {
  const last = useMemo(() => newestMessage(messages), [messages])
  const [expired, setExpired] = useState<string>()

  const waiting = awaitsAgent(last, messages)
  const id = last?.id
  const deadline = last ? time(last) + WORKING_TIMEOUT_MS : 0
  useEffect(() => {
    if (!waiting || id === undefined) return
    const timer = setTimeout(() => setExpired(id), Math.max(0, deadline - Date.now()))
    return () => clearTimeout(timer)
  }, [waiting, id, deadline])

  return waiting && expired !== id
}

/** The newest of `messages` by creation time; the working timer counts from it (ADR 059). */
export function newestMessage(messages: ChatMessage[]): ChatMessage | undefined {
  let newest: ChatMessage | undefined
  for (const message of messages) {
    if (!newest || time(message) >= time(newest)) newest = message
  }
  return newest
}

function time(message: ChatMessage) {
  return parseApiTimestamp(message.createdAt).getTime()
}
