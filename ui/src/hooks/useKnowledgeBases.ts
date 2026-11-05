import { useCallback, useContext, useMemo, useRef, useState } from 'react'
import {
  type CreateKnowledgeBaseRequest,
  type UpdateKnowledgeBaseRequest,
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase,
  uploadDocument,
} from '../api/api'
import { fileProblem } from '../components/knowledgeBases/knowledgeBases'
import {
  KnowledgeBasesContext,
  type KnowledgeBasesValue,
  type UploadRow,
} from '../contexts/knowledgeBasesContext'
import { errorMessage } from '../utils/text'
import { useRemoteData } from './useRemoteData'

/**
 * Loads the knowledge bases and their changes; each change reloads the list. For
 * `KnowledgeBasesProvider`. Deleting one also removes it from every agent's `allowedKbs`
 * (ADR 014), so a caller showing agents reloads them.
 */
export function useKnowledgeBasesState(): KnowledgeBasesValue {
  const { data, loading, error, reload } = useRemoteData(listKnowledgeBases)
  const [uploads, setUploads] = useState<UploadRow[]>([])
  const nextUploadKey = useRef(0)

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
      setUploads((current) => current.filter((row) => row.kbId !== knowledgeBaseId))
      reload()
    },
    [reload],
  )

  const startUpload = useCallback((kbId: string, file: File) => {
    const key = nextUploadKey.current++
    const problem = fileProblem(file) ?? undefined
    setUploads((current) => [{ key, kbId, name: file.name, problem }, ...current])
    if (problem) return
    const setRow = (patch: Partial<UploadRow>) =>
      setUploads((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
    uploadDocument(kbId, file).then(
      (document) => setRow({ documentId: document.id }),
      (failure: unknown) => setRow({ problem: errorMessage(failure) }),
    )
  }, [])
  const dropUpload = useCallback(
    (key: number) => setUploads((current) => current.filter((row) => row.key !== key)),
    [],
  )

  return useMemo(
    () => ({
      knowledgeBases: data ?? [],
      loading,
      error,
      reload,
      create,
      update,
      remove,
      uploads,
      startUpload,
      dropUpload,
    }),
    [data, loading, error, reload, create, update, remove, uploads, startUpload, dropUpload],
  )
}

/** The shared knowledge-base list; only inside `KnowledgeBasesProvider`. */
export function useKnowledgeBases(): KnowledgeBasesValue {
  const value = useContext(KnowledgeBasesContext)
  if (!value) throw new Error('useKnowledgeBases must be used inside KnowledgeBasesProvider')
  return value
}
