import { useState } from 'react'
import { cx } from '../../utils/cx'
import Markdown from './Markdown'
import { type Step, type ToolCall, decodeToolResult, formatArguments } from './messages'

/**
 * What the agent did for one user message (V1 elements 6 and 7): its steps on one rail, then its
 * answers. Only the last step shows until "Show N earlier steps" (V4 C2 B), and each tool result
 * stays closed until asked for (V4 C3 C). While `working`, dots after the last step say so
 * (V1 element 9 A, ADR 055).
 */
export default function AgentRun({
  agentName,
  steps,
  answers,
  working = false,
}: {
  agentName: string
  steps: Step[]
  answers: { id: string; content: string | null }[]
  working?: boolean
}) {
  const [showAll, setShowAll] = useState(false)
  const earlier = steps.length - 1
  const shown = showAll ? steps : steps.slice(-1)

  return (
    <article aria-label={agentName} className="rounded-ui border border-line bg-surface px-7 py-6">
      <p className="m-0 mb-4.5 text-sm font-semibold text-muted">{agentName}</p>
      {steps.length > 0 && (
        <ol
          aria-label="Agent steps"
          className="m-0 flex list-none flex-col gap-5 border-l border-field py-0 pr-0 pl-6.5"
        >
          {earlier > 0 && (
            <li className="relative">
              <Dot className="border border-field bg-surface" />
              <button
                type="button"
                aria-expanded={showAll}
                onClick={() => setShowAll(!showAll)}
                className="cursor-pointer text-sm text-ink underline decoration-dotted"
              >
                {showAll
                  ? 'Hide earlier steps'
                  : `Show ${earlier === 1 ? '1 earlier step' : `${earlier} earlier steps`}`}
              </button>
            </li>
          )}
          {shown.map((step) => {
            const number = steps.indexOf(step) + 1
            return [
              step.think !== null && (
                <li key={`${step.id}-think`} className="relative">
                  <Dot className="bg-think" />
                  <p className="m-0 mb-1 text-xs font-semibold text-think-ink">
                    Step {number}, Think
                  </p>
                  <Markdown text={step.think} />
                </li>
              ),
              ...step.calls.map((toolCall) => (
                <li key={`${step.id}-${toolCall.call.id}`} className="relative">
                  <Dot className="bg-execute" />
                  <p className="m-0 mb-2 text-xs font-semibold text-execute-ink">
                    Step {number}, Execute: <span className="font-mono">{toolCall.call.name}</span>
                  </p>
                  <Call toolCall={toolCall} />
                </li>
              )),
            ]
          })}
        </ol>
      )}
      {answers.map((answer) => (
        <div
          key={answer.id}
          className={cx(steps.length > 0 && 'mt-5.5 border-t border-hairline pt-4.5')}
        >
          <Markdown text={answer.content ?? ''} className="text-base" />
        </div>
      ))}
      {working && (
        <div
          role="status"
          className={cx(
            'flex items-center gap-2.5 text-sm text-muted',
            (steps.length > 0 || answers.length > 0) && 'mt-5.5 border-t border-hairline pt-4.5',
          )}
        >
          <span aria-hidden="true" className="flex gap-1.5">
            <span className="size-1.75 rounded-full bg-think motion-safe:animate-pulse" />
            <span className="size-1.75 rounded-full bg-think/60 motion-safe:animate-pulse" />
            <span className="size-1.75 rounded-full bg-think/30 motion-safe:animate-pulse" />
          </span>
          {agentName} is working
          {steps.length > 0 &&
            `, ${steps.length === 1 ? '1 step' : `${steps.length} steps`} so far`}
        </div>
      )}
    </article>
  )
}

function Dot({ className }: { className: string }) {
  return (
    <span
      aria-hidden="true"
      className={cx('absolute top-1.5 -left-8 box-border size-2.5 rounded-full', className)}
    />
  )
}

function Call({ toolCall }: { toolCall: ToolCall }) {
  const [open, setOpen] = useState(false)
  const { call, result } = toolCall
  const decoded = result === undefined ? undefined : decodeToolResult(result)

  return (
    <div className="overflow-hidden rounded-ui bg-ground">
      <pre className="m-0 px-3.5 py-2.5 font-mono text-xs whitespace-pre-wrap">
        {formatArguments(call.arguments)}
      </pre>
      {decoded === undefined ? (
        <p className="m-0 border-t border-line px-3.5 py-2 text-sm text-muted">No result yet</p>
      ) : (
        <>
          {open &&
            (decoded.kind === 'markdown' ? (
              <div className="border-t border-line px-3.5 py-2">
                <Markdown text={decoded.text} className="text-sm" />
              </div>
            ) : (
              <pre className="m-0 border-t border-line px-3.5 py-2.5 font-mono text-xs whitespace-pre-wrap">
                {decoded.text}
              </pre>
            ))}
          <div className="border-t border-line px-3.5 py-2">
            <button
              type="button"
              aria-expanded={open}
              onClick={() => setOpen(!open)}
              className="cursor-pointer text-sm text-ink underline decoration-dotted"
            >
              {open ? 'Hide the result' : 'Show the result'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
