import { type ReactNode, useState } from 'react'
import { matchPath, useLocation, useNavigate } from 'react-router'
import type { KnowledgeBase } from '../api/api'
import { path } from '../api/http'
import { toCreateRequest, toUpdateRequest } from '../components/knowledgeBases/knowledgeBases'
import DeleteKnowledgeBaseModal from '../components/modals/DeleteKnowledgeBaseModal'
import KnowledgeBaseFormModal from '../components/modals/KnowledgeBaseFormModal'
import { useAgents } from './useAgents'
import { useKnowledgeBases } from './useKnowledgeBases'
import { useReturnFocus } from './useReturnFocus'

type Open =
  | { kind: 'create' }
  | { kind: 'edit'; knowledgeBase: KnowledgeBase }
  | { kind: 'delete'; knowledgeBase: KnowledgeBase }

/**
 * The create, edit, and delete dialogs for knowledge bases, opened from the list and from a
 * knowledge base's page. Render `dialogs` once where the hook is used.
 */
export function useKnowledgeBaseDialogs(): {
  openCreate: () => void
  openEdit: (knowledgeBase: KnowledgeBase) => void
  openDelete: (knowledgeBase: KnowledgeBase) => void
  dialogs: ReactNode
} {
  const [open, setOpen] = useState<Open | null>(null)
  const focus = useReturnFocus(open !== null)
  const show = (next: Open) => {
    focus.remember()
    setOpen(next)
  }
  const { create, update, remove } = useKnowledgeBases()
  const agents = useAgents()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const close = () => setOpen(null)

  let dialogs: ReactNode = null
  if (open?.kind === 'create') {
    dialogs = (
      <KnowledgeBaseFormModal
        onClose={close}
        onSave={async (draft) => {
          const knowledgeBase = await create(toCreateRequest(draft))
          close()
          navigate(path`/knowledge-base/${knowledgeBase.id}`)
        }}
      />
    )
  } else if (open?.kind === 'edit') {
    const { knowledgeBase } = open
    dialogs = (
      <KnowledgeBaseFormModal
        knowledgeBase={knowledgeBase}
        onClose={close}
        onSave={async (draft) => {
          const request = toUpdateRequest(knowledgeBase, draft)
          if (Object.keys(request).length > 0) await update(knowledgeBase.id, request)
          close()
        }}
      />
    )
  } else if (open?.kind === 'delete') {
    const { knowledgeBase } = open
    dialogs = (
      <DeleteKnowledgeBaseModal
        knowledgeBase={knowledgeBase}
        onClose={close}
        onDelete={async () => {
          await remove(knowledgeBase.id)
          // The backend took it out of every agent's knowledge bases (ADR 014).
          agents.reload()
          close()
          if (
            matchPath('/knowledge-base/:knowledgeBaseId', pathname)?.params.knowledgeBaseId ===
            knowledgeBase.id
          )
            navigate('/knowledge-base')
        }}
      />
    )
  }

  return {
    openCreate: () => show({ kind: 'create' }),
    openEdit: (knowledgeBase) => show({ kind: 'edit', knowledgeBase }),
    openDelete: (knowledgeBase) => show({ kind: 'delete', knowledgeBase }),
    dialogs,
  }
}
