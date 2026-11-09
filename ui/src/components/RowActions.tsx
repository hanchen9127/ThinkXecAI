import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown, Popconfirm } from 'antd'
import { useEffect, useId, useState } from 'react'
import { useMenuTrigger } from '../hooks/useMenuTrigger'

/**
 * A list row's ⋯ menu with Rename and Delete; Delete asks first, on the row (V3 K9 A, V4 C6 C).
 * `onDelete` runs after the confirmation; the caller then waits for Undo.
 *
 * Keyboard (critique of 2026-11-08): the menu takes focus when it opens, so arrow keys move in it;
 * the question puts focus on Cancel, and closing it without deleting puts focus back on ⋯. A
 * confirmed delete lets go of focus, as the row leaves; the Undo bar then takes it (DeleteFeedback).
 * `focusOnMount` puts focus on ⋯ when the row comes back from renaming, which replaced it.
 */
export default function RowActions({
  name,
  deleteDescription,
  onRename,
  onDelete,
  focusOnMount = false,
}: {
  name: string
  /** What else goes with it, shown under "Delete {name}?". */
  deleteDescription: string
  onRename: () => void
  /** Called with ⋯, for focus to return to it if the delete is undone. */
  onDelete: (returnTo: HTMLElement | null) => void
  focusOnMount?: boolean
}) {
  const [confirming, setConfirming] = useState(false)
  const { trigger, then } = useMenuTrigger()
  const cancelId = useId()
  // Read once: only a row that appears just after renaming takes focus.
  const [focusAtStart] = useState(focusOnMount)
  useEffect(() => {
    if (focusAtStart) trigger.current?.focus()
  }, [focusAtStart, trigger])
  // The question is drawn a frame or two after it opens; focus its Cancel once it is there.
  useEffect(() => {
    if (!confirming) return
    let frame = 0
    let tries = 0
    const focusCancel = () => {
      const cancel = document.getElementById(cancelId)
      if (cancel) cancel.focus()
      else if (++tries < 10) frame = requestAnimationFrame(focusCancel)
    }
    frame = requestAnimationFrame(focusCancel)
    return () => cancelAnimationFrame(frame)
  }, [confirming, cancelId])
  const keep = () => {
    setConfirming(false)
    trigger.current?.focus()
  }

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
      cancelButtonProps={{ id: cancelId, onKeyDown: (e) => e.key === 'Escape' && keep() }}
      onConfirm={() => {
        setConfirming(false)
        ;(document.activeElement as HTMLElement | null)?.blur()
        onDelete(trigger.current)
      }}
      onCancel={keep}
    >
      <span>
        <Dropdown
          trigger={['click']}
          autoFocus
          // A closed menu leaves the page, so a row that folds away leaves no menu behind.
          destroyOnHidden
          menu={{
            items: [
              {
                key: 'rename',
                label: 'Rename',
                onClick: onRename,
              },
              {
                key: 'delete',
                label: 'Delete',
                danger: true,
                onClick: then(() => setConfirming(true)),
              },
            ],
          }}
        >
          <Button
            ref={trigger}
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
