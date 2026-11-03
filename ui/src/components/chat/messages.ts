import type { ChatMessage, ToolCallMetadata } from '../../api/api'
import { parseApiTimestamp } from '../../utils/time'

/**
 * One tool call of a step, with its result once the tool message is stored; `ms` is the time from
 * the call to its result (ADR 059).
 */
export interface ToolCall {
  call: ToolCallMetadata
  result: string | undefined
  ms: number | undefined
}

/**
 * One model call that asked for tools (V1 element 7): what it said, then each call; `thinkMs` is the
 * time from the message before it to this one.
 */
export interface Step {
  id: string
  think: string | null
  thinkMs: number | undefined
  calls: ToolCall[]
}

/**
 * What the history shows: the user's message, or what the agent did in reply. A run's `ms` is the
 * time from the user's message to its last answer, once it has one.
 */
export type HistoryItem =
  | { kind: 'user'; message: ChatMessage }
  | { kind: 'agent'; id: string; steps: Step[]; answers: ChatMessage[]; ms: number | undefined }

/**
 * Groups a session's messages into the user's messages and the agent's runs. Everything between two
 * user messages is one run: assistant messages with tool calls become steps (their tool messages
 * attach by call id), the others are answers or notices (ADR 043). `system` messages are not shown.
 * Times come from consecutive messages' creation times (ADR 059).
 */
export function groupHistory(messages: ChatMessage[]): HistoryItem[] {
  const ordered = [...messages].sort(
    (a, b) => parseApiTimestamp(a.createdAt).getTime() - parseApiTimestamp(b.createdAt).getTime(),
  )
  const time = (message: ChatMessage) => parseApiTimestamp(message.createdAt).getTime()
  const results = new Map<string, { data: string; at: number }>()
  for (const message of ordered) {
    const response = message.role === 'tool' ? message.metadata?.toolResponse : undefined
    if (response) results.set(response.id, { data: response.responseData, at: time(message) })
  }

  const items: HistoryItem[] = []
  let run: Extract<HistoryItem, { kind: 'agent' }> | null = null
  let question: number | undefined
  let previous: number | undefined
  for (const message of ordered) {
    if (message.role === 'user') {
      run = null
      question = previous = time(message)
      items.push({ kind: 'user', message })
      continue
    }
    if (message.role === 'tool') previous = time(message)
    if (message.role !== 'assistant') continue
    if (!run) {
      run = { kind: 'agent', id: message.id, steps: [], answers: [], ms: undefined }
      items.push(run)
    }
    const at = time(message)
    const toolCalls = message.metadata?.toolCalls ?? []
    if (toolCalls.length > 0) {
      run.steps.push({
        id: message.id,
        think: message.content?.trim() ? message.content : null,
        thinkMs: previous === undefined ? undefined : at - previous,
        calls: toolCalls.map((call) => {
          const result = results.get(call.id)
          return { call, result: result?.data, ms: result && result.at - at }
        }),
      })
    } else if (message.content?.trim()) {
      run.answers.push(message)
      run.ms = question === undefined ? undefined : at - question
    }
    previous = at
  }
  return items
}

/**
 * Stored tool results are JSON strings, exactly what the model saw (tech-stack → Agent Runtime);
 * one level is decoded. Text (such as the database tool's Markdown table) is shown as Markdown,
 * anything else as indented JSON, and a result that is not JSON as it was stored.
 */
export function decodeToolResult(responseData: string): {
  kind: 'markdown' | 'json' | 'raw'
  text: string
} {
  let value: unknown
  try {
    value = JSON.parse(responseData)
  } catch {
    return { kind: 'raw', text: responseData }
  }
  if (typeof value === 'string') return { kind: 'markdown', text: value }
  return { kind: 'json', text: JSON.stringify(value, null, 2) }
}

/**
 * The one line shown for a tool result before it is opened (ADR 059): the row count of a table, the
 * item count of a list, or the start of its first line.
 */
export function summarizeResult(responseData: string): string {
  const { kind, text } = decodeToolResult(responseData)
  if (kind === 'json') {
    const value: unknown = JSON.parse(text)
    if (Array.isArray(value)) return value.length === 1 ? '1 item' : `${value.length} items`
    return cut(JSON.stringify(value))
  }
  const lines = text.split('\n').map((line) => line.trim())
  if (kind === 'markdown' && lines[0]?.startsWith('|') && /^\|[\s:|-]+\|$/.test(lines[1] ?? '')) {
    const rows = lines.slice(2).findIndex((line) => !line.startsWith('|'))
    const count = rows === -1 ? lines.length - 2 : rows
    return count === 1 ? '1 row' : `${count} rows`
  }
  return cut(lines.find((line) => line !== '') ?? '')
}

function cut(first: string) {
  return first.length > SUMMARY_LENGTH ? `${first.slice(0, SUMMARY_LENGTH).trimEnd()}…` : first
}

const SUMMARY_LENGTH = 80

/** A duration as the trace shows it: "0.4 s", "12.0 s", "1 min 5 s". */
export function formatDuration(ms: number): string {
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`
  const seconds = Math.round(ms / 1000)
  return `${Math.floor(seconds / 60)} min ${seconds % 60} s`
}

/** A tool call's arguments as `name: value` lines; the raw text when they are not a JSON object. */
export function formatArguments(args: string): string {
  let value: unknown
  try {
    value = JSON.parse(args)
  } catch {
    return args
  }
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return args
  const entries = Object.entries(value)
  if (entries.length === 0) return 'no arguments'
  return entries
    .map(([key, v]) => `${key}: ${typeof v === 'string' ? v : JSON.stringify(v)}`)
    .join('\n')
}

/** Chat titles are at most 200 characters (ADR 032); one from a first message is kept short. */
export const TITLE_LENGTH = 200
const SHORT_TITLE = 60

/** A new chat's title: the first line of its first message, cut at a word near 60 characters. */
export function titleFrom(content: string): string {
  const line = content.trim().split('\n')[0].trim().replace(/\s+/g, ' ')
  if (line.length <= SHORT_TITLE) return line
  const cut = line.slice(0, SHORT_TITLE)
  const space = cut.lastIndexOf(' ')
  return `${(space > SHORT_TITLE / 2 ? cut.slice(0, space) : cut).trimEnd()}…`
}

/** What a chat is called in lists and its header; a chat may have no title. */
export function chatTitle(session: { title: string | null }): string {
  return session.title?.trim() ? session.title : 'Untitled chat'
}
