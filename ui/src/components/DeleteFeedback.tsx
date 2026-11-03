import { CloseOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import type { useUndoableDelete } from '../hooks/useUndoableDelete'
import { errorMessage } from '../utils/text'

/** Under a list: the Undo bar while a delete waits (V3 K9), and the error when one failed. */
export default function DeleteFeedback({
  deletes,
}: {
  deletes: ReturnType<typeof useUndoableDelete>
}) {
  return (
    <>
      {deletes.pending && (
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-ui bg-ink px-3 py-2.5 text-sm text-white"
        >
          <span className="min-w-0 break-words">{deletes.pending.name} deleted</span>
          <button
            type="button"
            onClick={deletes.undo}
            className="cursor-pointer font-semibold text-white underline"
          >
            Undo
          </button>
        </div>
      )}
      {deletes.failure && (
        <div role="alert" className="flex items-start justify-between gap-2 text-sm text-danger">
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
    </>
  )
}
