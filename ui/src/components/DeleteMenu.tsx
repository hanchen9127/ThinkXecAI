import { MoreOutlined } from '@ant-design/icons'
import { Button, Dropdown } from 'antd'

/**
 * A page header's ⋯ menu holding Delete, so the header shows no red button (ADR 058). Named without
 * the item: the list row of the same item has "More actions for {name}".
 */
export default function DeleteMenu({ onDelete }: { onDelete: () => void }) {
  return (
    <Dropdown
      trigger={['click']}
      menu={{ items: [{ key: 'delete', label: 'Delete', danger: true, onClick: onDelete }] }}
    >
      <Button type="text" aria-label="More actions" icon={<MoreOutlined />} />
    </Dropdown>
  )
}
