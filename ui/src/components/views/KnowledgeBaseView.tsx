import { CloseOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, Spin, Upload } from 'antd'
import { Fragment, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import type { Agent, KnowledgeBase } from '../../api/api'
import { path } from '../../api/http'
import { useAgents } from '../../hooks/useAgents'
import { useDocuments } from '../../hooks/useDocuments'
import { useKnowledgeBaseDialogs } from '../../hooks/useKnowledgeBaseDialogs'
import { useKnowledgeBases } from '../../hooks/useKnowledgeBases'
import { useUndoableDelete } from '../../hooks/useUndoableDelete'
import { parseApiTimestamp } from '../../utils/time'
import { countLabel, errorMessage } from '../../utils/text'
import DocumentRow from '../knowledgeBases/DocumentRow'
import { agentsUsing, fileProblem } from '../knowledgeBases/knowledgeBases'

/** `/knowledge-base` and `/knowledge-base/:knowledgeBaseId`. */
export default function KnowledgeBaseView() {
  const { knowledgeBaseId } = useParams()
  const { knowledgeBases, loading, error } = useKnowledgeBases()

  if (!knowledgeBaseId) {
    return (
      <div className="flex flex-col gap-2 px-12 py-9">
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
      <div className="flex flex-col gap-3 px-12 py-9">
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
            <Link to="/knowledge-base" className="text-think">
              Back to knowledge bases
            </Link>
          </>
        )}
      </div>
    )
  }
  // A new page per knowledge base: uploads, renames, and a waiting delete belong to one.
  return <KnowledgeBasePage key={knowledgeBase.id} knowledgeBase={knowledgeBase} />
}

interface UploadRow {
  key: number
  name: string
  /** Set once the server has stored it; the row stays until the list shows the document. */
  documentId?: string
  problem?: string
}

function KnowledgeBasePage({ knowledgeBase }: { knowledgeBase: KnowledgeBase }) {
  const { openEdit, openDelete, dialogs } = useKnowledgeBaseDialogs()
  const users = agentsUsing(useAgents().agents, knowledgeBase.id)
  const { documents, loading, error, reload, upload, update, remove } = useDocuments(
    knowledgeBase.id,
  )
  const deletes = useUndoableDelete(remove)
  const [uploads, setUploads] = useState<UploadRow[]>([])
  const nextKey = useRef(0)

  const shown = useMemo(
    () =>
      documents
        .filter((document) => !deletes.hidden.has(document.id))
        // Newest upload first; the backend orders by last change, which a rename would move.
        .sort(
          (a, b) =>
            parseApiTimestamp(b.createdAt).getTime() - parseApiTimestamp(a.createdAt).getTime(),
        ),
    [documents, deletes.hidden],
  )
  const listed = new Set(documents.map((document) => document.id))
  const rows = uploads.filter((row) => !(row.documentId && listed.has(row.documentId)))

  function start(file: File) {
    const key = nextKey.current++
    const problem = fileProblem(file)
    setUploads((current) => [{ key, name: file.name, problem: problem ?? undefined }, ...current])
    if (problem) return
    upload(file).then(
      (document) => setRow(key, { documentId: document.id }),
      (failure: unknown) => setRow(key, { problem: errorMessage(failure) }),
    )
  }
  function setRow(key: number, patch: Partial<UploadRow>) {
    setUploads((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }
  const dismiss = (key: number) => setUploads((current) => current.filter((row) => row.key !== key))

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line bg-surface px-12 pt-6 pb-5">
        <div className="min-w-0">
          <h1 className="m-0 text-xl font-semibold">{knowledgeBase.name}</h1>
          {knowledgeBase.description && (
            <p className="m-0 mt-0.5 text-sm text-muted">{knowledgeBase.description}</p>
          )}
          <UsedBy agents={users} />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => openEdit(knowledgeBase)}>Edit</Button>
          <Button danger onClick={() => openDelete(knowledgeBase)}>
            Delete
          </Button>
        </div>
      </header>
      {dialogs}

      <div className="box-border flex w-full max-w-220 flex-col gap-6 px-12 py-8">
        <Upload.Dragger
          accept=".md,.markdown"
          multiple={false}
          showUploadList={false}
          beforeUpload={(file) => {
            start(file)
            return Upload.LIST_IGNORE
          }}
        >
          <UploadOutlined className="text-icon text-think" />
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
              <button type="button" onClick={reload} className="text-think underline">
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
          {(rows.length > 0 || shown.length > 0) && (
            <ul className="m-0 list-none border-t border-hairline p-0">
              {rows.map((row) => (
                <UploadItem key={row.key} row={row} onDismiss={() => dismiss(row.key)} />
              ))}
              {shown.map((document) => (
                <DocumentRow
                  key={document.id}
                  document={document}
                  onRename={(name) => update(document.id, { name }).then(() => undefined)}
                  onDelete={() => deletes.confirm({ id: document.id, name: document.name })}
                />
              ))}
            </ul>
          )}
          {deletes.pending && (
            <div
              role="status"
              className="flex items-center justify-between gap-3 rounded-ui bg-ink px-3.5 py-2.5 text-white"
            >
              <span>{deletes.pending.name} deleted</span>
              <button
                type="button"
                onClick={deletes.undo}
                className="cursor-pointer font-semibold text-think-wash underline"
              >
                Undo
              </button>
            </div>
          )}
          {deletes.failure && (
            <div role="alert" className="flex items-start justify-between gap-3 text-danger">
              <span>
                Couldn't delete {deletes.failure.name}. {errorMessage(deletes.failure.error)}
              </span>
              <Button
                type="text"
                size="small"
                aria-label="Dismiss"
                icon={<CloseOutlined />}
                onClick={deletes.dismissFailure}
              />
            </div>
          )}
        </section>
      </div>
    </>
  )
}

/** "Used by A and B", each linked to its profile (V3 K3 B). */
function UsedBy({ agents }: { agents: Agent[] }) {
  if (agents.length === 0) return <p className="m-0 mt-1.5">Not used by any agent yet.</p>
  return (
    <p className="m-0 mt-1.5">
      Used by{' '}
      {agents.map((agent, i) => (
        <Fragment key={agent.id}>
          {i > 0 && (i === agents.length - 1 ? ' and ' : ', ')}
          <Link to={path`/agent/${agent.id}`} className="text-think">
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
          <div role="status" className="text-sm text-think-ink">
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
