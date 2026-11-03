import { useCallback, useMemo } from 'react'
import { useKnowledgeBases } from './useKnowledgeBases'

/** Names an agent's `allowedKbs` ids; an id not (yet) in the list reads "Unknown knowledge base". */
export function useKnowledgeBaseNames(): (ids: string[]) => string[] {
  const { knowledgeBases } = useKnowledgeBases()
  const names = useMemo(
    () => new Map(knowledgeBases.map((kb) => [kb.id, kb.name])),
    [knowledgeBases],
  )
  return useCallback(
    (ids: string[]) => ids.map((id) => names.get(id) ?? 'Unknown knowledge base'),
    [names],
  )
}
