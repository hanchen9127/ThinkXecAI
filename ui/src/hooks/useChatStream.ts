import { useEffect, useRef, useState } from 'react'
import { type ChatMessage, type SseMessageEvent, getChatSession, getSseUrl } from '../api/api'
import { ApiError } from '../api/http'

/**
 * `connecting` until `init`; `open` while live; `replaced` once another tab took the chat
 * (ADR 048); `stopped` when the browser gave up and the chat still exists, or could not be checked.
 */
export type StreamStatus = 'connecting' | 'open' | 'replaced' | 'stopped'

export interface StreamHandlers {
  /** The stream (re)opened: load the history again, for anything produced while disconnected. */
  onInit: () => void
  /** A message the agent runtime stored and pushed (`AI_GENERATED_CONTENT`). */
  onMessage: (message: ChatMessage) => void
  /** The chat no longer exists: the browser's reconnect got a real 404 (ADR 047, 049). */
  onGone: () => void
}

/**
 * Keeps an `EventSource` open for one chat (tech-stack → SSE path). Other event types and malformed
 * data are ignored. `replaced` closes it without reconnecting; `reconnect` opens it again, which
 * takes the chat back from the other tab. The stream is closed when the chat closes. Nothing is
 * opened while `enabled` is false (a chat not known to exist).
 */
export function useChatStream(sessionId: string, handlers: StreamHandlers, enabled = true) {
  const [status, setStatus] = useState<StreamStatus>('connecting')
  const [problem, setProblem] = useState<unknown>()
  const [attempt, setAttempt] = useState(0)
  const latest = useRef(handlers)
  useEffect(() => {
    latest.current = handlers
  })

  useEffect(() => {
    if (!enabled) return
    let active = true
    const source = new EventSource(getSseUrl(sessionId))

    source.addEventListener('init', () => {
      setStatus('open')
      setProblem(undefined)
      latest.current.onInit()
    })
    source.addEventListener('message', (event) => {
      const message = generatedMessage((event as MessageEvent<string>).data)
      if (message) latest.current.onMessage(message)
    })
    source.addEventListener('replaced', () => {
      source.close()
      setStatus('replaced')
    })
    source.addEventListener('error', () => {
      // While CONNECTING the browser is retrying by itself; CLOSED means it gave up.
      if (source.readyState !== EventSource.CLOSED) return
      getChatSession(sessionId).then(
        () => {
          if (!active) return
          setProblem(undefined)
          setStatus('stopped')
        },
        (error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.code === 404) {
            latest.current.onGone()
            return
          }
          setProblem(error)
          setStatus('stopped')
        },
      )
    })

    return () => {
      active = false
      source.close()
    }
  }, [sessionId, attempt, enabled])

  return {
    status,
    /** Why the stream stopped, when checking the chat failed too. */
    problem,
    reconnect: () => {
      setStatus('connecting')
      setAttempt((n) => n + 1)
    },
  }
}

function generatedMessage(data: string): ChatMessage | null {
  let event: SseMessageEvent
  try {
    event = JSON.parse(data) as SseMessageEvent
  } catch {
    return null
  }
  if (event?.type !== 'AI_GENERATED_CONTENT') return null
  const message = event.payload?.message
  return message && typeof message.id === 'string' ? message : null
}
