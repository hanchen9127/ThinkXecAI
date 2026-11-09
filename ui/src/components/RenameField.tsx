import { Button, Input } from 'antd'
import { useId, useState } from 'react'
import { errorMessage } from '../utils/text'

/**
 * Renames something in its own list row (V3 K7 A, V4 C6 C): Enter saves, Esc cancels. A blank name is
 * refused, an unchanged one closes without saving, and the backend's refusal keeps the field open.
 */
export default function RenameField({
  label,
  value,
  maxLength,
  onSave,
  onClose,
}: {
  label: string
  value: string
  maxLength: number
  onSave: (value: string) => Promise<void>
  onClose: () => void
}) {
  const [text, setText] = useState(value)
  const [problem, setProblem] = useState<string>()
  const [saving, setSaving] = useState(false)
  const errorId = useId()

  async function save() {
    const trimmed = text.trim()
    if (trimmed === '') {
      setProblem('Enter a name.')
      return
    }
    if (trimmed === value) {
      onClose()
      return
    }
    setSaving(true)
    setProblem(undefined)
    try {
      await onSave(trimmed)
      onClose()
    } catch (error) {
      setProblem(`Couldn't rename it. ${errorMessage(error)}`)
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <Input
        autoFocus
        variant="underlined"
        aria-label={label}
        aria-invalid={problem ? true : undefined}
        aria-describedby={problem ? errorId : undefined}
        status={problem ? 'error' : undefined}
        value={text}
        maxLength={maxLength}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') void save()
          if (e.key === 'Escape') onClose()
        }}
      />
      {problem && (
        <span id={errorId} className="text-xs text-danger">
          {problem}
        </span>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="primary" size="small" loading={saving} onClick={() => void save()}>
          Save
        </Button>
        <Button size="small" onClick={onClose}>
          Cancel
        </Button>
        <span className="text-xs text-muted">Enter saves, Esc cancels</span>
      </div>
    </div>
  )
}
