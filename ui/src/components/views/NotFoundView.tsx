import { Link } from 'react-router'
import { COLUMN } from '../../layout/column'
import { cx } from '../../utils/cx'

export default function NotFoundView() {
  return (
    <div className={cx(COLUMN, 'flex flex-col gap-3 py-9')}>
      <h1 className="m-0 text-xl font-semibold">Page not found</h1>
      <p className="m-0 text-muted">There is nothing at this address.</p>
      <Link to="/chat" className="text-ink underline">
        Go to chats
      </Link>
    </div>
  )
}
