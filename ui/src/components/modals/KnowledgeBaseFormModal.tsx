import { Alert, Input, Modal } from 'antd'
import { useState } from 'react'
import type { KnowledgeBase } from '../../api/api'
import { errorMessage } from '../../utils/text'
import {
  type DraftErrors,
  type KnowledgeBaseDraft,
  LIMITS,
  draftFrom,
  emptyDraft,
  validate,
} from '../knowledgeBases/knowledgeBases'

/**
 * Creates a knowledge base, or edits `knowledgeBase` (V3 K2 A: a small dialog). `onSave` gets the
 * draft and throws the backend's error to show; the dialog stays open until it succeeds.
 */
export default function KnowledgeBaseFormModal({
  knowledgeBase,
  onSave,
  onClose,
}: {
  knowledgeBase?: KnowledgeBase
  onSave: (draft: KnowledgeBaseDraft) => Promise<void>
  onClose: () => void
}) {
  const [draft, setDraft] = useState<KnowledgeBaseDraft>(() =>
    knowledgeBase ? draftFrom(knowledgeBase) : emptyDraft(),
  )
  const [errors, setErrors] = useState<DraftErrors>({})
  const [saveError, setSaveError] = useState<unknown>()
  const [saving, setSaving] = useState(false)

  async function save() {
    const found = validate(draft)
    setErrors(found)
    if (found.name) return
    setSaving(true)
    setSaveError(undefined)
    try {
      await onSave(draft)
    } catch (error) {
      setSaveError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      title={knowledgeBase ? `Edit ${knowledgeBase.name}` : 'New knowledge base'}
      okText={knowledgeBase ? 'Save changes' : 'Create knowledge base'}
      onOk={save}
      onCancel={onClose}
      confirmLoading={saving}
      destroyOnHidden
    >
      <div className="flex flex-col gap-4 py-2">
        {saveError !== undefined && (
          <Alert
            type="error"
            showIcon
            title="Couldn't save the knowledge base."
            description={errorMessage(saveError)}
          />
        )}
        <div className="flex flex-col gap-1">
          <label htmlFor="kb-name" className="text-xs font-semibold text-muted">
            Name
          </label>
          <Input
            id="kb-name"
            variant="underlined"
            value={draft.name}
            maxLength={LIMITS.name}
            status={errors.name ? 'error' : undefined}
            aria-invalid={errors.name ? true : undefined}
            aria-describedby={errors.name ? 'kb-name-error' : undefined}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
          {errors.name && (
            <span id="kb-name-error" className="text-xs text-danger">
              {errors.name}
            </span>
          )}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="kb-description" className="text-xs font-semibold text-muted">
            Description
          </label>
          <Input.TextArea
            id="kb-description"
            rows={4}
            value={draft.description}
            maxLength={LIMITS.description}
            onChange={(e) => setDraft({ ...draft, description: e.target.value })}
          />
          <span className="text-xs text-muted">Optional. Shown on its page.</span>
        </div>
      </div>
    </Modal>
  )
}
