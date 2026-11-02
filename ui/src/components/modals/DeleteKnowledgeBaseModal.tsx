import { Alert, Modal } from 'antd'
import { useMemo, useState } from 'react'
import { type KnowledgeBase, listDocumentsByKb } from '../../api/api'
import { useAgents } from '../../hooks/useAgents'
import { useRemoteData } from '../../hooks/useRemoteData'
import { countLabel, errorMessage } from '../../utils/text'
import { agentsUsing } from '../knowledgeBases/knowledgeBases'

/**
 * Says what deleting a knowledge base changes before it is confirmed (V3 K8 B): its documents go,
 * and every agent that searched it loses it (ADR 014).
 */
export default function DeleteKnowledgeBaseModal({
  knowledgeBase,
  onDelete,
  onClose,
}: {
  knowledgeBase: KnowledgeBase
  onDelete: () => Promise<void>
  onClose: () => void
}) {
  const loadDocuments = useMemo(() => () => listDocumentsByKb(knowledgeBase.id), [knowledgeBase.id])
  const documents = useRemoteData(loadDocuments).data
  const agents = agentsUsing(useAgents().agents, knowledgeBase.id)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<unknown>()

  async function confirm() {
    setDeleting(true)
    setDeleteError(undefined)
    try {
      await onDelete()
    } catch (error) {
      setDeleteError(error)
      setDeleting(false)
    }
  }

  const count = documents?.length
  // Until the count arrives the documents are named without one; none means nothing to delete.
  const its =
    count === undefined
      ? 'its documents'
      : count === 0
        ? null
        : `its ${countLabel(count, 'document', 'documents')}`
  return (
    <Modal
      open
      title={`Delete ${knowledgeBase.name}?`}
      okText="Delete knowledge base"
      okButtonProps={{ danger: true }}
      onOk={confirm}
      onCancel={onClose}
      confirmLoading={deleting}
      destroyOnHidden
    >
      <div className="flex flex-col gap-3">
        {agents.length > 0 ? (
          <>
            <p className="m-0">
              {its ? `This deletes ${its} and removes it` : 'This removes it'} from these agents:
            </p>
            <ul aria-label="Agents that lose it" className="m-0 pl-5">
              {agents.map((agent) => (
                <li key={agent.id}>{agent.name}</li>
              ))}
            </ul>
          </>
        ) : (
          <p className="m-0">
            {its
              ? `This deletes ${its}. No agent uses it.`
              : 'It has no documents, and no agent uses it.'}
          </p>
        )}
        <p className="m-0 text-muted">This can't be undone.</p>
        {deleteError !== undefined && (
          <Alert type="error" showIcon title={errorMessage(deleteError)} />
        )}
      </div>
    </Modal>
  )
}
