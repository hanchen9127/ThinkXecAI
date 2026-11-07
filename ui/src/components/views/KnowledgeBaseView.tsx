import { CloseOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, Spin, Upload } from 'antd'
import { Fragment, useEffect, useMemo } from 'react'
import { Link, useParams } from 'react-router'
import type { Agent, KnowledgeBase } from '../../api/api'
import { path } from '../../api/http'
import type { UploadRow } from '../../contexts/knowledgeBasesContext'
import { useAgents } from '../../hooks/useAgents'
import { useDocuments } from '../../hooks/useDocuments'
import { useKnowledgeBaseDialogs } from '../../hooks/useKnowledgeBaseDialogs'
import { useKnowledgeBases } from '../../hooks/useKnowledgeBases'
import { useUndoableDelete } from '../../hooks/useUndoableDelete'
import { COLUMN } from '../../layout/column'
import BackToList from '../BackToList'
import { cx } from '../../utils/cx'
import { parseApiTimestamp } from '../../utils/time'
import { countLabel, errorMessage } from '../../utils/text'
import DeleteFeedback from '../DeleteFeedback'
import DeleteMenu from '../DeleteMenu'
import DocumentRow from '../knowledgeBases/DocumentRow'
import { agentsUsing } from '../knowledgeBases/knowledgeBases'

/** `/knowledge-base` and `/knowledge-base/:knowledgeBaseId`. */
export default function KnowledgeBaseView() {
  const { knowledgeBaseId } = useParams()
  const { knowledgeBases, loading, error } = useKnowledgeBases()

  if (!knowledgeBaseId) {
    return (
      <div className={cx(COLUMN, 'flex flex-col gap-2 py-9')}>
        <h1 className="m-0 text-xl font-semibold">Knowledge bases</h1>
        <p className="m-0 text-muted">
          Choose a knowledge base from the list to see its documents, or create one with New.
        </p>
      </div>
    )
  }

  const knowledgeBase = knowledgeBases.find((kb) => kb.id === knowledgeBaseId)
  if (!knowledgeBase) {
    return (
      <div className={cx(COLUMN, 'flex flex-col gap-3 py-9')}>
        {loading ? (
          <p className="m-0 text-muted">Loading the knowledge base…</p>
        ) : error !== undefined ? (
          <p role="alert" className="m-0 text-muted">
            Couldn't load knowledge bases. {errorMessage(error)}
          </p>
        ) : (
          <>
            <h1 className="m-0 text-xl font-semibold">Knowledge base not found</h1>
            <p className="m-0 text-muted">It may have been deleted.</p>
            <Link to="/knowledge-base" className="text-ink underline">
              Back to knowledge bases
            </Link>
          </>
        )}
      </div>
    )
  }
  // A new page per knowledge base: renames and a waiting delete belong to one.
  return <KnowledgeBasePage key={knowledgeBase.id} knowledgeBase={knowledgeBase} />
}

function KnowledgeBasePage({ knowledgeBase }: { knowledgeBase: KnowledgeBase }) {
  const { openEdit, openDelete, dialogs } = useKnowledgeBaseDialogs()
  const users = agentsUsing(useAgents().agents, knowledgeBase.id)
  const { documents, loading, error, reload, update, remove } = useDocuments(knowledgeBase.id)
  const { uploads, startUpload, dropUpload } = useKnowledgeBases()
  const deletes = useUndoableDelete(remove)

  // Newest upload first; the backend orders by last change, which a rename would move. A deleted
  // document stays listed while its row folds away (ADR 061).
  const listedDocuments = useMemo(
    () =>
      [...documents].sort(
        (a, b) =>
          parseApiTimestamp(b.createdAt).getTime() - parseApiTimestamp(a.createdAt).getTime(),
      ),
    [documents],
  )
  const shown = listedDocuments.filter((document) => !deletes.hidden.has(document.id))
  const listed = new Set(documents.map((document) => document.id))
  const own = uploads.filter((row) => row.kbId === knowledgeBase.id)
  const isListed = (row: UploadRow) => row.documentId !== undefined && listed.has(row.documentId)
  const rows = own.filter((row) => !isListed(row))

  // An upload can finish after this page loaded, even one started before it opened.
  const stored = rows.flatMap((row) => row.documentId ?? []).join()
  useEffect(() => {
    if (stored) reload()
  }, [stored, reload])

  // A finished row leaves for good once the list shows its document; kept, it would come back as
  // "indexing" when that document is deleted.
  const finished = own.filter(isListed).map((row) => row.key)
  useEffect(() => finished.forEach(dropUpload))

  return (
    <>
      <header className="border-b border-line bg-surface">
        <div className={cx(COLUMN, 'flex flex-wrap items-start justify-between gap-4 pt-6 pb-5')}>
          <div className="flex min-w-0 items-start gap-1">
            <BackToList className="-mt-2 -ml-3" />
            <div className="min-w-0">
              <h1 className="m-0 text-xl font-semibold">{knowledgeBase.name}</h1>
              {knowledgeBase.description && (
                <p className="m-0 mt-0.5 text-sm text-muted">{knowledgeBase.description}</p>
              )}
              <UsedBy agents={users} />
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => openEdit(knowledgeBase)}>Edit</Button>
            <DeleteMenu onDelete={() => openDelete(knowledgeBase)} />
          </div>
        </div>
      </header>
      {dialogs}

      <div className={cx(COLUMN, 'flex flex-col gap-6 py-8')}>
        <Upload.Dragger
          accept=".md,.markdown"
          multiple={false}
          showUploadList={false}
          beforeUpload={(file) => {
            startUpload(knowledgeBase.id, file)
            return Upload.LIST_IGNORE
          }}
        >
          <UploadOutlined className="text-icon text-muted" />
          <p className="m-0 mt-1.5">Drop a Markdown file here, or choose one</p>
          <p className="m-0 text-xs text-muted">.md or .markdown, up to 1 MB</p>
        </Upload.Dragger>

        <section aria-label="Documents" className="flex flex-col gap-2">
          <h2 className="m-0 text-base font-semibold">
            {shown.length === 0 ? 'Documents' : countLabel(shown.length, 'document', 'documents')}
          </h2>
          {error !== undefined && (
            <div role="alert" className="flex flex-col items-start gap-2 text-muted">
              <p className="m-0">Couldn't load the documents. {errorMessage(error)}</p>
              <button type="button" onClick={reload} className="cursor-pointer text-ink underline">
                Try again
              </button>
            </div>
          )}
          {loading && documents.length === 0 && (
            <p className="m-0 text-muted">Loading documents…</p>
          )}
          {!loading && error === undefined && shown.length === 0 && rows.length === 0 && (
            <p className="m-0 text-muted">No documents yet. Upload one above.</p>
          )}
          {(rows.length > 0 || listedDocuments.length > 0) && (
            <ul className="m-0 list-none border-t border-hairline p-0">
              {rows.map((row) => (
                <UploadItem key={row.key} row={row} onDismiss={() => dropUpload(row.key)} />
              ))}
              {listedDocuments.map((document) => (
                <DocumentRow
                  key={document.id}
                  document={document}
                  leaving={deletes.hidden.has(document.id)}
                  onRename={(name) => update(document.id, { name }).then(() => undefined)}
                  onDelete={() => deletes.confirm({ id: document.id, name: document.name })}
                />
              ))}
            </ul>
          )}
          <DeleteFeedback deletes={deletes} />
        </section>
      </div>
    </>
  )
}

/** "Used by A and B", each linked to its profile (V3 K3 B). */
function UsedBy({ agents }: { agents: Agent[] }) {
  if (agents.length === 0) return <p className="m-0 mt-1.5 text-sm">Not used by any agent yet.</p>
  return (
    <p className="m-0 mt-1.5 text-sm">
      Used by{' '}
      {agents.map((agent, i) => (
        <Fragment key={agent.id}>
          {i > 0 && (i === agents.length - 1 ? ' and ' : ', ')}
          <Link to={path`/agent/${agent.id}`} className="text-ink underline">
            {agent.name}
          </Link>
        </Fragment>
      ))}
    </p>
  )
}

/** A file being uploaded, or one that was not (V3 K6 A); a failure stays until dismissed. */
function UploadItem({ row, onDismiss }: { row: UploadRow; onDismiss: () => void }) {
  if (!row.problem) {
    return (
      <li className="flex items-center justify-between gap-3 border-b border-hairline bg-ground px-3 py-2.5">
        <div>
          <div className="font-medium">{row.name}</div>
          <div role="status" className="text-sm text-muted">
            Reading its sections and indexing them…
          </div>
        </div>
        <Spin size="small" />
      </li>
    )
  }
  return (
    <li className="flex items-start justify-between gap-3 border-b border-hairline bg-danger-wash px-3 py-2.5">
      <div>
        <div className="font-medium">{row.name}</div>
        <div role="alert" className="text-sm text-danger">
          Not uploaded: {row.problem}
        </div>
      </div>
      <Button
        type="text"
        size="small"
        aria-label={`Dismiss the error for ${row.name}`}
        icon={<CloseOutlined />}
        onClick={onDismiss}
      />
    </li>
  )
}
