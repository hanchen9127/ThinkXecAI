import { useState } from 'react'
import type { Document } from '../../api/api'
import { formatApiTimestamp } from '../../utils/time'
import Expandable from '../Expandable'
import RenameField from '../RenameField'
import RowActions from '../RowActions'
import { LIMITS } from './knowledgeBases'

/**
 * One document (V3 K4 B): its name and upload time, renamed in the row (K7 A) and deleted after a
 * confirmation on the row (K9 A; `onDelete` then waits for Undo).
 */
export default function DocumentRow({
  document,
  leaving = false,
  onRename,
  onDelete,
}: {
  document: Document
  /** Deleted, waiting for Undo: the row folds away (ADR 061). */
  leaving?: boolean
  onRename: (name: string) => Promise<void>
  onDelete: (returnTo: HTMLElement | null) => void
}) {
  const [editing, setEditing] = useState(false)
  // Back from renaming, focus returns to the row's ⋯, not to nowhere.
  const [renamed, setRenamed] = useState(false)

  return (
    <Expandable
      as="li"
      open={!leaving}
      motion="fold"
      className="border-b border-hairline px-3 py-2.5 transition hover:bg-ground"
    >
      {editing ? (
        <RenameField
          label={`Name of ${document.name}`}
          value={document.name}
          maxLength={LIMITS.name}
          onSave={onRename}
          onClose={() => {
            setEditing(false)
            setRenamed(true)
          }}
        />
      ) : (
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="font-medium break-words">{document.name}</div>
            <div className="text-sm text-muted">
              Uploaded {formatApiTimestamp(document.createdAt)}
            </div>
          </div>
          <RowActions
            name={document.name}
            deleteDescription="Agents stop finding its sections."
            onRename={() => setEditing(true)}
            focusOnMount={renamed}
            onDelete={onDelete}
          />
        </div>
      )}
    </Expandable>
  )
}
