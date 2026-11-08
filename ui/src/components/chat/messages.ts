import type { ChatMessage, ToolCallMetadata, ToolResponseMetadata } from '../../api/api'
import { parseApiTimestamp } from '../../utils/time'

/** Why a tool call failed, as the backend recorded it (ADR 064); null when it succeeded. */
export type ToolFailure = ToolResponseMetadata['failure']

/**
 * One tool call of a step, with its result once the tool message is stored; `ms` is the time from
 * the call to its result (ADR 059), `failure` why it failed, if it did (ADR 064).
 */
export interface ToolCall {
  call: ToolCallMetadata
  result: string | undefined
  ms: number | undefined
  failure: ToolFailure
}

/** A failure kind as the trace says it, before the result's own text (ADR 059, amended 2026-11-08). */
export const FAILURE_WORDS: Record<NonNullable<ToolFailure>, string> = {
  REFUSED: 'Refused',
  INVALID_ARGUMENTS: 'Wrong arguments',
  UNKNOWN_TOOL: 'Unknown tool',
  TOOL_ERROR: 'Failed',
}

/**
 * The folded line's count of failed calls: "2 refused" when every failure was a rule declining the
 * call, shown calmly as a rule doing its job, "2 failed" otherwise; null when none failed.
 */
export function failedLabel(calls: ToolCall[]): { text: string; refused: boolean } | null {
  const failures = calls.flatMap(({ failure }) => (failure ? [failure] : []))
  if (failures.length === 0) return null
  const refused = failures.every((failure) => failure === 'REFUSED')
  return { text: `${failures.length} ${refused ? 'refused' : 'failed'}`, refused }
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
/** The tool that ends a run when called in a reply with text (ADR 045). */
const TERMINATE = 'terminate'

/** A reply with text that called `terminate`: the run ends with it, and its text is the answer. */
export function endsRun(reply: ChatMessage | undefined) {
  return (
    !!reply?.content?.trim() && !!reply.metadata?.toolCalls?.some((call) => call.name === TERMINATE)
  )
}

export function groupHistory(messages: ChatMessage[]): HistoryItem[] {
  const ordered = [...messages].sort(
    (a, b) => parseApiTimestamp(a.createdAt).getTime() - parseApiTimestamp(b.createdAt).getTime(),
  )
  const time = (message: ChatMessage) => parseApiTimestamp(message.createdAt).getTime()
  const results = new Map<string, { data: string; at: number; failure: ToolFailure }>()
  for (const message of ordered) {
    const response = message.role === 'tool' ? message.metadata?.toolResponse : undefined
    // Tool messages saved before ADR 064 have no failure: they read as successes.
    if (response) {
      results.set(response.id, {
        data: response.responseData,
        at: time(message),
        failure: response.failure ?? null,
      })
    }
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
    const last = endsRun(message)
    // `terminate` only closes the run; any other tool called with it is still a step.
    const calls = last ? toolCalls.filter((call) => call.name !== TERMINATE) : toolCalls
    if (calls.length > 0) {
      run.steps.push({
        id: message.id,
        think: !last && message.content?.trim() ? message.content : null,
        thinkMs: previous === undefined ? undefined : at - previous,
        calls: calls.map((call) => {
          const result = results.get(call.id)
          return {
            call,
            result: result?.data,
            ms: result && result.at - at,
            failure: result?.failure ?? null,
          }
        }),
      })
    }
    if (last || (toolCalls.length === 0 && message.content?.trim())) {
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

/**
 * Whether an answer is one of the notices a run ends with when it gave no answer: stopped by the user
 * (ADR 060), failed, or out of steps (ADR 043). The texts are `AgentNotices` in the backend.
 */
export function isNotice(content: string | null): boolean {
  return NOTICE.test(content ?? '')
}

/** The notice of a run the user stopped (ADR 060), unlike the ones of a run that failed. */
export const STOPPED = 'Stopped.'

const NOTICE =
  /^(?:Stopped\.$|The agent could not finish this request: |The agent stopped after \d+ steps )/

/**
 * The question Try again sends, or null. Only a chat that ends on a notice has one: anything after
 * the notice means the chat moved on, and sending again could repeat an answered question. Every
 * question gets one reply, but a reply can come after later questions (ADR 060), and a message sent
 * during a run splits that run in two. So replies are counted against questions over the whole chat:
 * one still owed means a run is going on, and the notice is not about the last question. A run that
 * died without a reply stays owed, which only hides Try again.
 */
export function retryQuestion(history: HistoryItem[]): string | null {
  const run = history.at(-1)
  const question = history.at(-2)
  if (run?.kind !== 'agent' || question?.kind !== 'user') return null
  if (!isNotice(run.answers.at(-1)?.content ?? null)) return null
  let owed = 0
  for (const item of history) {
    owed = item.kind === 'user' ? owed + 1 : Math.max(0, owed - item.answers.length)
  }
  return owed === 0 ? question.message.content : null
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
