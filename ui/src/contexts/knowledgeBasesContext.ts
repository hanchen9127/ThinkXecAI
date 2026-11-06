import { createContext } from 'react'
import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBase,
  UpdateKnowledgeBaseRequest,
} from '../api/api'

/** A file on its way into a knowledge base, or one that was not accepted. */
export interface UploadRow {
  key: number
  kbId: string
  name: string
  /** Set once the server has stored it; the row stays until the list shows the document. */
  documentId?: string
  problem?: string
}

export interface KnowledgeBasesValue {
  knowledgeBases: KnowledgeBase[]
  loading: boolean
  error: unknown
  reload: () => void
  create: (request: CreateKnowledgeBaseRequest) => Promise<KnowledgeBase>
  update: (knowledgeBaseId: string, request: UpdateKnowledgeBaseRequest) => Promise<KnowledgeBase>
  remove: (knowledgeBaseId: string) => Promise<void>
  /** Here and not on the page: an upload goes on when its page is left. */
  uploads: UploadRow[]
  startUpload: (knowledgeBaseId: string, file: File) => void
  dropUpload: (key: number) => void
}

export const KnowledgeBasesContext = createContext<KnowledgeBasesValue | null>(null)
