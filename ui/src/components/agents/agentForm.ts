import type {
  Agent,
  AgentModel,
  ChatOptions,
  ChatOptionsInput,
  CreateAgentRequest,
  UpdateAgentRequest,
} from '../../api/api'
import { AGENT_MODELS } from '../../api/api'

/** Request size limits (ADR 032) and chat-option ranges (ADR 015). */
export const LIMITS = {
  name: 200,
  description: 2000,
  systemPrompt: 20000,
  listEntries: 50,
  temperature: { min: 0, max: 2, step: 0.1 },
  topP: { min: 0, max: 1, step: 0.05 },
  messageLength: { min: 1, max: 100, step: 1 },
} as const

/** The backend's defaults for a new agent (tech-stack Agent Runtime). */
export const DEFAULT_CHAT_OPTIONS: ChatOptions = { temperature: 0.7, topP: 1, messageLength: 10 }

/** What the agent form edits; text fields are never null here. */
export interface AgentDraft {
  name: string
  description: string
  systemPrompt: string
  model: AgentModel
  allowedTools: string[]
  allowedKbs: string[]
  chatOptions: ChatOptions
}

export function emptyDraft(): AgentDraft {
  return {
    name: '',
    description: '',
    systemPrompt: '',
    model: AGENT_MODELS[0],
    allowedTools: [],
    allowedKbs: [],
    chatOptions: { ...DEFAULT_CHAT_OPTIONS },
  }
}

export function draftFrom(agent: Agent): AgentDraft {
  return {
    name: agent.name,
    description: agent.description ?? '',
    systemPrompt: agent.systemPrompt ?? '',
    model: agent.model,
    allowedTools: [...agent.allowedTools],
    allowedKbs: [...agent.allowedKbs],
    chatOptions: { ...agent.chatOptions },
  }
}

export type DraftErrors = Partial<Record<'name', string>>

/** What the backend would refuse and the form can say first; limits are also enforced by the inputs. */
export function validate(draft: AgentDraft): DraftErrors {
  return draft.name.trim() === '' ? { name: 'Enter a name.' } : {}
}

export function toCreateRequest(draft: AgentDraft): CreateAgentRequest {
  return {
    name: draft.name.trim(),
    model: draft.model,
    description: draft.description.trim() || undefined,
    systemPrompt: draft.systemPrompt.trim() || undefined,
    allowedTools: draft.allowedTools,
    allowedKbs: draft.allowedKbs,
    chatOptions: draft.chatOptions,
  }
}

/**
 * Only what changed: the backend merges an update field by field, chat options included
 * (ADR 015), so an untouched field is never sent back. Empty means nothing to save.
 */
export function toUpdateRequest(agent: Agent, draft: AgentDraft): UpdateAgentRequest {
  const request: UpdateAgentRequest = {}
  const name = draft.name.trim()
  if (name !== agent.name) request.name = name
  if (draft.description.trim() !== (agent.description ?? '')) {
    request.description = draft.description.trim()
  }
  if (draft.systemPrompt.trim() !== (agent.systemPrompt ?? '')) {
    request.systemPrompt = draft.systemPrompt.trim()
  }
  if (draft.model !== agent.model) request.model = draft.model
  if (!sameList(draft.allowedTools, agent.allowedTools)) request.allowedTools = draft.allowedTools
  if (!sameList(draft.allowedKbs, agent.allowedKbs)) request.allowedKbs = draft.allowedKbs

  const options: ChatOptionsInput = {}
  for (const key of ['temperature', 'topP', 'messageLength'] as const) {
    if (draft.chatOptions[key] !== agent.chatOptions[key]) options[key] = draft.chatOptions[key]
  }
  if (Object.keys(options).length > 0) request.chatOptions = options
  return request
}

function sameList(a: string[], b: string[]) {
  return a.length === b.length && a.every((value, i) => value === b[i])
}
