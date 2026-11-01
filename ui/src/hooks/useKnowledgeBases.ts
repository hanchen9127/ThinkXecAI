import { useCallback, useContext, useMemo } from 'react'
import {
  type CreateKnowledgeBaseRequest,
  type UpdateKnowledgeBaseRequest,
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../api/api'
import { KnowledgeBasesContext, type KnowledgeBasesValue } from '../contexts/knowledgeBasesContext'
import { useRemoteData } from './useRemoteData'

/**
 * Loads the knowledge bases and their changes; each change reloads the list. For
 * `KnowledgeBasesProvider`. Deleting one also removes it from every agent's `allowedKbs`
 * (ADR 014), so a caller showing agents reloads them.
 */
export function useKnowledgeBasesState(): KnowledgeBasesValue {
  const { data, loading, error, reload } = useRemoteData(listKnowledgeBases)

  const create = useCallback(
    async (request: CreateKnowledgeBaseRequest) => {
      const knowledgeBase = await createKnowledgeBase(request)
      reload()
      return knowledgeBase
    },
    [reload],
  )
  const update = useCallback(
    async (knowledgeBaseId: string, request: UpdateKnowledgeBaseRequest) => {
      const knowledgeBase = await updateKnowledgeBase(knowledgeBaseId, request)
      reload()
      return knowledgeBase
    },
    [reload],
  )
  const remove = useCallback(
    async (knowledgeBaseId: string) => {
      await deleteKnowledgeBase(knowledgeBaseId)
      reload()
    },
    [reload],
  )

  return useMemo(
    () => ({ knowledgeBases: data ?? [], loading, error, reload, create, update, remove }),
    [data, loading, error, reload, create, update, remove],
  )
}

/** The shared knowledge-base list; only inside `KnowledgeBasesProvider`. */
export function useKnowledgeBases(): KnowledgeBasesValue {
  const value = useContext(KnowledgeBasesContext)
  if (!value) throw new Error('useKnowledgeBases must be used inside KnowledgeBasesProvider')
  return value
}
