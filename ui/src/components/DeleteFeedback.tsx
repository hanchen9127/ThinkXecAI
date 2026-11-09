import { CloseOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useEffect, useRef, useState } from 'react'
import type { useUndoableDelete } from '../hooks/useUndoableDelete'
import Expandable from './Expandable'
import { errorMessage } from '../utils/text'

/**
 * Under a list: the Undo bar while a delete waits (V3 K9), rising into place and folding away again
 * after Undo or the wait (ADR 061), and the error when one failed. It sticks to the bottom of the
 * panel that scrolls, so a delete far down a long list still shows its Undo (critique of
 * 2026-11-08), and it takes focus when the delete left focus nowhere. Undo puts focus back on the
 * row's ⋯, once the row is no longer inert, as the bar folding away would leave it nowhere.
 */
export default function DeleteFeedback({
  deletes,
}: {
  deletes: ReturnType<typeof useUndoableDelete>
}) {
  // The name stays while the bar folds away.
  const [last, setLast] = useState(deletes.pending)
  if (deletes.pending && deletes.pending !== last) setLast(deletes.pending)
  const undo = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!deletes.pending) return
    const timer = setTimeout(() => {
      const lost = document.activeElement === null || document.activeElement === document.body
      if (lost) undo.current?.focus()
    })
    return () => clearTimeout(timer)
  }, [deletes.pending])

  return (
    <div className="sticky bottom-0 z-10 flex flex-col gap-2">
      <Expandable open={deletes.pending !== null} motion="rise">
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-ui bg-ink px-3 py-2.5 text-sm text-white shadow-md"
        >
          <span className="min-w-0 break-words">{last?.name} deleted</span>
          <button
            ref={undo}
            type="button"
            onClick={() => {
              const returnTo = last?.returnTo
              deletes.undo()
              setTimeout(() => returnTo?.focus())
            }}
            className="cursor-pointer font-semibold text-white underline focus-visible:outline-white"
          >
            Undo
          </button>
        </div>
      </Expandable>
      {deletes.failure && (
        <div
          role="alert"
          className="flex items-start justify-between gap-2 bg-surface text-sm text-danger"
        >
          <span>
            Couldn't delete {deletes.failure.name}. {errorMessage(deletes.failure.error)}
          </span>
          <Button
            type="text"
            size="small"
            aria-label="Dismiss"
            icon={<CloseOutlined />}
            onClick={deletes.dismissFailure}
          />
        </div>
      )}
    </div>
  )
}
