import type {
  Agent,
  CreateKnowledgeBaseRequest,
  KnowledgeBase,
  UpdateKnowledgeBaseRequest,
} from '../../api/api'

/** Request size limits (ADR 032) and the upload limits (tech-stack → upload document). */
export const LIMITS = {
  name: 200,
  description: 2000,
  /** Spring's `1MB` is 1024 × 1024 bytes. */
  uploadBytes: 1024 * 1024,
  uploadName: 200,
} as const

/** What the knowledge-base form edits; the description is never null here. */
export interface KnowledgeBaseDraft {
  name: string
  description: string
}

export function emptyDraft(): KnowledgeBaseDraft {
  return { name: '', description: '' }
}

export function draftFrom(knowledgeBase: KnowledgeBase): KnowledgeBaseDraft {
  return { name: knowledgeBase.name, description: knowledgeBase.description ?? '' }
}

export type DraftErrors = Partial<Record<'name', string>>

export function validate(draft: KnowledgeBaseDraft): DraftErrors {
  return draft.name.trim() === '' ? { name: 'Enter a name.' } : {}
}

export function toCreateRequest(draft: KnowledgeBaseDraft): CreateKnowledgeBaseRequest {
  return { name: draft.name.trim(), description: draft.description.trim() || undefined }
}

/** Only what changed; empty means nothing to save. */
export function toUpdateRequest(
  knowledgeBase: KnowledgeBase,
  draft: KnowledgeBaseDraft,
): UpdateKnowledgeBaseRequest {
  const request: UpdateKnowledgeBaseRequest = {}
  const name = draft.name.trim()
  if (name !== knowledgeBase.name) request.name = name
  const description = draft.description.trim()
  if (description !== (knowledgeBase.description ?? '')) request.description = description
  return request
}

/**
 * Why the backend would refuse this file, checked before sending it; null when it may be sent.
 * The content checks (UTF-8, at least one heading) are left to the backend.
 */
export function fileProblem(file: File): string | null {
  if (!/\.(md|markdown)$/i.test(file.name)) return 'only .md or .markdown files.'
  if (file.name.length > LIMITS.uploadName) return 'the file name is longer than 200 characters.'
  if (file.size === 0) return 'the file is empty.'
  if (file.size > LIMITS.uploadBytes) return 'larger than 1 MB.'
  return null
}

/** The agents that search this knowledge base, in the agent list's order. */
export function agentsUsing(agents: Agent[], knowledgeBaseId: string): Agent[] {
  return agents.filter((agent) => agent.allowedKbs.includes(knowledgeBaseId))
}
