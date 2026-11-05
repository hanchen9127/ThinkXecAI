import { COLUMN } from '../../layout/column'
import { cx } from '../../utils/cx'

/** `/agent` with no agent open. */
export default function AgentHomeView() {
  return (
    <div className={cx(COLUMN, 'flex flex-col gap-2 py-9')}>
      <h1 className="m-0 text-xl font-semibold">Agents</h1>
      <p className="m-0 text-muted">Choose an agent from the list to see how it is set up.</p>
    </div>
  )
}
