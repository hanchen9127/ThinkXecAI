import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown, Popconfirm } from 'antd'
import { useState } from 'react'

/**
 * A list row's ⋯ menu with Rename and Delete; Delete asks first, on the row (V3 K9 A, V4 C6 C).
 * `onDelete` runs after the confirmation; the caller then waits for Undo.
 */
export default function RowActions({
  name,
  deleteDescription,
  onRename,
  onDelete,
}: {
  name: string
  /** What else goes with it, shown under "Delete {name}?". */
  deleteDescription: string
  onRename: () => void
  onDelete: () => void
}) {
  const [confirming, setConfirming] = useState(false)

  return (
    <Popconfirm
      open={confirming}
      onOpenChange={(open) => {
        // Opened only from the menu; a click elsewhere closes it.
        if (!open) setConfirming(false)
      }}
      title={`Delete ${name}?`}
      description={deleteDescription}
      okText="Delete"
      okButtonProps={{ danger: true }}
      cancelText="Cancel"
      onConfirm={() => {
        setConfirming(false)
        onDelete()
      }}
      onCancel={() => setConfirming(false)}
    >
      <span>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'rename', label: 'Rename', onClick: onRename },
              { key: 'delete', label: 'Delete', danger: true, onClick: () => setConfirming(true) },
            ],
          }}
        >
          <Button
            type="text"
            size="small"
            aria-label={`More actions for ${name}`}
            icon={<MoreOutlined />}
          />
        </Dropdown>
      </span>
    </Popconfirm>
  )
}
