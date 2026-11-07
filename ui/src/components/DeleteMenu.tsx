import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown } from 'antd'
import { useMenuTrigger } from '../hooks/useMenuTrigger'

/**
 * A page header's ⋯ menu holding Delete, so the header shows no red button (ADR 058). Named without
 * the item: the list row of the same item has "More actions for {name}". Focus returns to the button
 * when the dialog Delete opens is closed (Phase 14, D6).
 */
export default function DeleteMenu({ onDelete }: { onDelete: () => void }) {
  const { trigger, then } = useMenuTrigger()
  return (
    <Dropdown
      trigger={['click']}
      menu={{ items: [{ key: 'delete', label: 'Delete', danger: true, onClick: then(onDelete) }] }}
    >
      <Button ref={trigger} type="text" aria-label="More actions" icon={<MoreOutlined />} />
    </Dropdown>
  )
}
