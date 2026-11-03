import type { ReactNode } from 'react'
import { useKnowledgeBasesState } from '../hooks/useKnowledgeBases'
import { KnowledgeBasesContext } from './knowledgeBasesContext'

/** One knowledge-base list shared by its tab, its views, and agents (which name their `allowedKbs`). */
export default function KnowledgeBasesProvider({ children }: { children: ReactNode }) {
  return (
    <KnowledgeBasesContext.Provider value={useKnowledgeBasesState()}>
      {children}
    </KnowledgeBasesContext.Provider>
  )
}
