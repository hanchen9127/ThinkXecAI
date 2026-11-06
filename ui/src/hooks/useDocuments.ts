import { useCallback, useMemo } from 'react'
import {
  type UpdateDocumentRequest,
  deleteDocument,
  listDocumentsByKb,
  updateDocument,
} from '../api/api'
import { useRemoteData } from './useRemoteData'

/**
 * The documents of one knowledge base and the changes to them; each change reloads the list.
 * Uploads belong to `useKnowledgeBases`, because they outlive the page that started them.
 * Without a knowledge base nothing is loaded. Switching knowledge bases never shows the previous
 * one's documents, even when its answer arrives late.
 */
export function useDocuments(kbId: string | undefined) {
  const load = useMemo(() => (kbId ? () => listDocumentsByKb(kbId) : null), [kbId])
  const { data, loading, error, reload } = useRemoteData(load)

  const update = useCallback(
    async (documentId: string, request: UpdateDocumentRequest) => {
      const document = await updateDocument(documentId, request)
      reload()
      return document
    },
    [reload],
  )
  const remove = useCallback(
    async (documentId: string, options?: { keepalive?: boolean }) => {
      await deleteDocument(documentId, options)
      reload()
    },
    [reload],
  )

  return { documents: data ?? [], loading, error, reload, update, remove }
}
