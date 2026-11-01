import { useCallback } from 'react'
import {
  type CreateKnowledgeBaseRequest,
  type UpdateKnowledgeBaseRequest,
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
} from '../api/api'
import { useRemoteData } from './useRemoteData'

/**
 * The knowledge bases and the changes to them; each change reloads the list. Deleting one also
 * removes it from every agent's `allowedKbs` (ADR 014), so a caller showing agents reloads them.
 */
export function useKnowledgeBases() {
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

  return { knowledgeBases: data ?? [], loading, error, reload, create, update, remove }
}
