import { Alert, Input, type InputRef, Modal } from 'antd'
import { useMemo, useRef, useState } from 'react'
import { type Agent, listChatSessionsByAgent } from '../../api/api'
import { useRemoteData } from '../../hooks/useRemoteData'
import { errorMessage } from '../../utils/text'

/**
 * Asks for the agent's name before deleting it (V2 A8 C), and says what happens to its chats:
 * they stay (their agent becomes empty) but cannot take new messages (409).
 */
export default function DeleteAgentModal({
  agent,
  onDelete,
  onClose,
}: {
  agent: Agent
  onDelete: () => Promise<void>
  onClose: () => void
}) {
  const loadChats = useMemo(() => () => listChatSessionsByAgent(agent.id), [agent.id])
  const chats = useRemoteData(loadChats).data
  const [typed, setTyped] = useState('')
  const nameField = useRef<InputRef>(null)
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

  return (
    <Modal
      open
      title={`Delete ${agent.name}?`}
      okText="Delete agent"
      okButtonProps={{ danger: true, disabled: typed !== agent.name }}
      onOk={confirm}
      onCancel={onClose}
      confirmLoading={deleting}
      destroyOnHidden
      // The name is the one thing to do here; the dialog would otherwise focus its Close button.
      afterOpenChange={(open) => {
        if (open) nameField.current?.focus()
      }}
    >
      <div className="flex flex-col gap-4">
        <p className="m-0">{effect(chats?.length)} This can't be undone.</p>
        {deleteError !== undefined && (
          <Alert type="error" showIcon title={errorMessage(deleteError)} />
        )}
        <div className="flex flex-col gap-1">
          <label htmlFor="delete-agent-name" className="text-xs font-semibold text-muted">
            Type {agent.name} to confirm
          </label>
          <Input
            ref={nameField}
            id="delete-agent-name"
            variant="underlined"
            autoComplete="off"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  )
}

function effect(chats: number | undefined) {
  if (chats === undefined)
    return "Its chats stay readable, but you can't send new messages in them."
  if (chats === 0) return 'It has no chats.'
  return `Its ${chats === 1 ? 'chat stays' : `${chats} chats stay`} readable, but you can't send new messages in ${chats === 1 ? 'it' : 'them'}.`
}
