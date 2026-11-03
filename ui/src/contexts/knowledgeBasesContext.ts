import { createContext } from 'react'
import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBase,
  UpdateKnowledgeBaseRequest,
} from '../api/api'

export interface KnowledgeBasesValue {
  knowledgeBases: KnowledgeBase[]
  loading: boolean
  error: unknown
  reload: () => void
  create: (request: CreateKnowledgeBaseRequest) => Promise<KnowledgeBase>
  update: (knowledgeBaseId: string, request: UpdateKnowledgeBaseRequest) => Promise<KnowledgeBase>
  remove: (knowledgeBaseId: string) => Promise<void>
}

export const KnowledgeBasesContext = createContext<KnowledgeBasesValue | null>(null)
