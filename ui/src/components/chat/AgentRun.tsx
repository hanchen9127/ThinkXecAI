import { type ReactNode, useEffect, useState } from 'react'
import { cx } from '../../utils/cx'
import { countLabel } from '../../utils/text'
import { parseApiTimestamp } from '../../utils/time'
import Expandable from '../Expandable'
import Tag from '../Tag'
import Markdown from './Markdown'
import {
  FAILURE_WORDS,
  STOPPED,
  type Step,
  type ToolCall,
  decodeToolResult,
  failedLabel,
  formatArguments,
  formatDuration,
  isNotice,
  summarizeResult,
} from './messages'

/** One row of the trace: a Think, or one tool call of an Execute. */
type Row =
  | { kind: 'think'; key: string; text: string; ms: number | undefined }
  | { kind: 'execute'; key: string; toolCall: ToolCall }

/**
 * What the agent did for one user message, as a trace (ADR 059): no card, its steps on one rail,
 * then its answers. While it works every step shows, and a skeleton row with a timer counts from
 * `since`, its newest message; once it has answered, the steps fold into one line above the answer
 * ("4 steps in 6.8 s" and the tools used), opened with Show steps. A failed tool call has a cross on
 * the rail and its kind before its summary, and the folded line counts the failures. A run that ends
 * with a notice ("Stopped.", a failure) stays open, and its notices are a quiet line after a mark,
 * not an answer (ADR 059, amended 2026-11-08). With `onRetry`, the last notice is followed by Try
 * again (ADR 060).
 */
export default function AgentRun({
  agentName,
  steps,
  answers,
  ms,
  working = false,
  since,
  onRetry,
}: {
  agentName: string
  steps: Step[]
  answers: { id: string; content: string | null }[]
  /** From the user's message to the last answer. */
  ms?: number
  working?: boolean
  since?: string
  onRetry?: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const rows = toRows(steps)
  // Rows shown when the run first renders do not animate; later ones arrived live (ADR 061).
  const [initial] = useState(
    () => new Set([...rows.map((row) => row.key), ...(working ? [WAITING] : [])]),
  )
  const fresh = (key: string) => !initial.has(key)
  // A run that ended with a notice never got to an answer: its steps show where it ended.
  const endedWithNotice = isNotice(answers.at(-1)?.content ?? null)
  const folded = answers.length > 0 && !working && !endedWithNotice
  const lastNotice = answers.findLast((answer) => isNotice(answer.content))
  const calls = steps.flatMap((step) => step.calls)
  const tools = [...new Set(calls.map(({ call }) => call.name))]
  const failed = failedLabel(calls)

  return (
    <article aria-label={agentName} className="flex flex-col gap-3">
      {(rows.length > 0 || working) && (
        <div>
          {folded && rows.length > 0 && (
            <div className="flex flex-wrap items-baseline gap-2.5 text-sm text-muted">
              <span>
                {countLabel(rows.length, 'step', 'steps')}
                {ms !== undefined && ` in ${formatDuration(ms)}`}
              </span>
              {tools.map((tool) => (
                <Tag key={tool} kind="tool">
                  {tool}
                </Tag>
              ))}
              {failed && (
                <span className="inline-flex items-center gap-1 text-danger">
                  <Cross className="size-2.5" />
                  {failed}
                </span>
              )}
              <button
                type="button"
                aria-expanded={expanded}
                onClick={() => setExpanded(!expanded)}
                className="cursor-pointer text-ink underline"
              >
                {expanded ? 'Hide steps' : 'Show steps'}
              </button>
            </div>
          )}
          <Expandable open={!folded || expanded} motion="slide" className={cx(folded && 'pt-3')}>
            <ol aria-label="Agent steps" className="m-0 list-none p-0">
              {rows.map((row, i) => (
                <RailItem
                  key={row.key}
                  dot={row.kind === 'think' ? 'bg-think' : 'bg-execute'}
                  failed={row.kind === 'execute' && row.toolCall.failure !== null}
                  last={i === rows.length - 1 && !working}
                  fresh={fresh(row.key)}
                  grows={fresh(rows[i + 1]?.key ?? (working ? WAITING : ''))}
                >
                  {row.kind === 'think' ? (
                    <>
                      <StepLabel kind="Think" number={i + 1} ms={row.ms} />
                      <Markdown text={row.text} />
                    </>
                  ) : (
                    <>
                      <StepLabel kind="Execute" number={i + 1} ms={row.toolCall.ms} />
                      <Call toolCall={row.toolCall} />
                    </>
                  )}
                </RailItem>
              ))}
              {working && (
                <RailItem dot="bg-line" last fresh={fresh(WAITING)} grows={false}>
                  <Waiting agentName={agentName} steps={rows.length} since={since} />
                </RailItem>
              )}
            </ol>
          </Expandable>
        </div>
      )}
      {answers.map((answer) =>
        isNotice(answer.content) ? (
          <Notice
            key={answer.id}
            text={answer.content ?? ''}
            onRetry={answer === lastNotice ? onRetry : undefined}
          />
        ) : (
          <Markdown key={answer.id} text={answer.content ?? ''} className="max-w-prose text-base" />
        ),
      )}
    </article>
  )
}

/**
 * One line naming the trace's two kinds of step in their colours, for a new chat, where a first-time
 * user has not met them yet (Phase 14, D5 A). It lives here because the step colours belong to the
 * trace (ADR 058).
 */
export function StepsExplained() {
  return (
    <p className="m-0 text-sm text-muted">
      Each reply shows its work: <span className="font-semibold text-think-ink">Think</span> is the
      agent deciding what to do, <span className="font-semibold text-execute-ink">Execute</span> is
      a tool it runs.
    </p>
  )
}

const WAITING = 'waiting'

function toRows(steps: Step[]): Row[] {
  return steps.flatMap((step): Row[] => [
    ...(step.think === null
      ? []
      : [{ kind: 'think' as const, key: `${step.id}-think`, text: step.think, ms: step.thinkMs }]),
    ...step.calls.map((toolCall) => ({
      kind: 'execute' as const,
      key: `${step.id}-${toolCall.call.id}`,
      toolCall,
    })),
  ])
}

/**
 * A notice a run ended with, as a quiet line after a mark: a square for a stop, a circled "!" for a
 * failure (ADR 059, amended 2026-11-08). Plain text: the notices are the backend's fixed sentences.
 */
function Notice({ text, onRetry }: { text: string; onRetry: (() => void) | undefined }) {
  return (
    <p className="m-0 flex items-start gap-2 text-sm text-muted">
      {text === STOPPED ? <StopMark /> : <FailureMark />}
      <span className="min-w-0">
        <span>{text}</span>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="ml-2.5 cursor-pointer text-sm text-ink underline"
          >
            Try again
          </button>
        )}
      </span>
    </p>
  )
}

function StopMark() {
  return (
    <svg
      aria-hidden="true"
      data-mark="stopped"
      viewBox="0 0 12 12"
      className="mt-1 size-3 shrink-0 text-muted"
    >
      <rect x="2" y="2" width="8" height="8" rx="1" fill="currentColor" />
    </svg>
  )
}

function FailureMark() {
  return (
    <svg
      aria-hidden="true"
      data-mark="failed"
      viewBox="0 0 12 12"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      className="mt-1 size-3 shrink-0 text-danger"
    >
      <circle cx="6" cy="6" r="4.8" />
      <path d="M6 3.4v3.2M6 8.4v.2" />
    </svg>
  )
}

/** The danger cross of a failed tool call: a shape, as the danger red is close to Execute's orange. */
function Cross({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      data-mark="failed"
      viewBox="0 0 12 12"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      className={cx('shrink-0 text-danger', className)}
    >
      <path d="M2.5 2.5l7 7M9.5 2.5l-7 7" />
    </svg>
  )
}

/**
 * A rail row: the dot (a cross for a failed tool call), the line down to the next row, and the row's
 * content. A `fresh` row arrived while the run was watched and fades in; `grows` marks the line
 * under it, drawn when the next row arrives (index.css).
 */
function RailItem({
  dot,
  failed = false,
  last,
  fresh,
  grows,
  children,
}: {
  dot: string
  failed?: boolean
  last: boolean
  fresh: boolean
  grows: boolean
  children: ReactNode
}) {
  return (
    <li className="flex gap-3" data-fresh={fresh || undefined} data-grows={grows || undefined}>
      <div aria-hidden="true" className="rail-mark flex w-3.5 shrink-0 flex-col items-center">
        {failed ? (
          <>
            <span className="h-0.75 shrink-0" />
            <Cross className="rail-dot size-3" />
          </>
        ) : (
          <>
            <span className="h-1.5 shrink-0" />
            <span className={cx('rail-dot size-2 shrink-0 rounded-full', dot)} />
          </>
        )}
        {!last && <span className="rail-line w-px flex-1 bg-line" />}
      </div>
      <div className="rail-content flex min-w-0 flex-1 flex-col gap-1 pb-3">{children}</div>
    </li>
  )
}

function StepLabel({
  kind,
  number,
  ms,
}: {
  kind: 'Think' | 'Execute'
  number: number
  ms: number | undefined
}) {
  return (
    <p className="m-0 flex items-baseline gap-2 text-xs">
      <span
        className={cx('font-semibold', kind === 'Think' ? 'text-think-ink' : 'text-execute-ink')}
      >
        {kind}
      </span>
      <span className="text-muted">
        Step {number}
        {ms !== undefined && `, ${formatDuration(ms)}`}
      </span>
    </p>
  )
}

/** The next step's row as a skeleton with the time since the newest message (ADR 059). */
function Waiting({
  agentName,
  steps,
  since,
}: {
  agentName: string
  steps: number
  since: string | undefined
}) {
  return (
    <div role="status" className="flex items-center gap-2.5 pt-0.5">
      <span className="sr-only">
        {agentName} is working{steps > 0 && `, ${countLabel(steps, 'step', 'steps')} so far`}
      </span>
      <span aria-hidden="true" className="h-2.5 w-35 rounded-xs bg-line" />
      <span aria-hidden="true" className="h-2.5 w-17.5 rounded-xs bg-hairline" />
      {since !== undefined && <Elapsed since={since} />}
    </div>
  )
}

/** "0:03", counting up each second. */
function Elapsed({ since }: { since: string }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  const seconds = Math.max(0, Math.floor((now - parseApiTimestamp(since).getTime()) / 1000))
  return (
    <span aria-hidden="true" className="font-mono text-xs text-ink">
      {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
    </span>
  )
}

/** A tool call: the tool, a one-line summary of its result, and Show, which opens both in place. */
function Call({ toolCall }: { toolCall: ToolCall }) {
  const [open, setOpen] = useState(false)
  const { call, result, failure } = toolCall
  const decoded = result === undefined ? undefined : decodeToolResult(result)

  // One block, with the space above the result inside it: closed, a gap in the step would remain.
  return (
    <div>
      <div className="flex flex-wrap items-baseline gap-2">
        <Tag kind="tool">{call.name}</Tag>
        <span className={cx('min-w-0 text-sm wrap-anywhere', failure ? 'text-ink' : 'text-muted')}>
          {failure && <span className="font-semibold text-danger">{FAILURE_WORDS[failure]}: </span>}
          {result === undefined ? 'No result yet' : summarizeResult(result)}
        </span>
        <button
          type="button"
          aria-expanded={open}
          aria-label={`${open ? 'Hide' : 'Show'} ${call.name}`}
          onClick={() => setOpen(!open)}
          className="cursor-pointer text-sm text-ink underline"
        >
          {open ? 'Hide' : 'Show'}
        </button>
      </div>
      <Expandable open={open} motion="slide" className="pt-1">
        <div className="flex flex-col gap-2 rounded-ui border border-line bg-surface px-3 py-2.5">
          <pre className="m-0 font-mono text-xs wrap-anywhere whitespace-pre-wrap text-muted">
            {formatArguments(call.arguments)}
          </pre>
          {decoded !== undefined &&
            (decoded.kind === 'markdown' ? (
              <Markdown text={decoded.text} className="text-sm" />
            ) : (
              <pre className="m-0 font-mono text-xs wrap-anywhere whitespace-pre-wrap">
                {decoded.text}
              </pre>
            ))}
        </div>
      </Expandable>
    </div>
  )
}
