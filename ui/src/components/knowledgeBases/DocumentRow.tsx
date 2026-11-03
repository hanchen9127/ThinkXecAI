import { useState } from 'react'
import type { Document } from '../../api/api'
import { formatApiTimestamp } from '../../utils/time'
import RenameField from '../RenameField'
import RowActions from '../RowActions'
import { LIMITS } from './knowledgeBases'

/**
 * One document (V3 K4 B): its name and upload time, renamed in the row (K7 A) and deleted after a
 * confirmation on the row (K9 A; `onDelete` then waits for Undo).
 */
export default function DocumentRow({
  document,
  onRename,
  onDelete,
}: {
  document: Document
  onRename: (name: string) => Promise<void>
  onDelete: () => void
}) {
  const [editing, setEditing] = useState(false)

  return (
    <li className="border-b border-hairline px-3 py-2.5">
      {editing ? (
        <RenameField
          label={`Name of ${document.name}`}
          value={document.name}
          maxLength={LIMITS.name}
          onSave={onRename}
          onClose={() => setEditing(false)}
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
            onDelete={onDelete}
          />
        </div>
      )}
    </li>
  )
}
