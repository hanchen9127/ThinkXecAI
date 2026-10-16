import { BASE_URL, http } from './http'
import type { components, paths } from './schema'

// Request and response types are derived from schema.d.ts, which is generated from the backend's
// OpenAPI document (`npm run gen:api`). CI regenerates it and fails when it, or this file, no longer
// matches the backend. Only endpoints the document does not describe yet are typed by hand below.

// ---------------------------------------------------------------------------
// Type helpers
// ---------------------------------------------------------------------------

type Schemas = components['schemas']

type JsonContent<T> = T extends { content: { 'application/json': infer C } } ? C : never

/** The `data` of a successful response to `method` on `path` (the envelope is unwrapped by http.ts). */
type ResponseData<P extends keyof paths, M extends keyof paths[P]> = paths[P][M] extends {
  responses: { 200: infer R }
}
  ? JsonContent<R> extends { data: infer D }
    ? D
    : never
  : never

/** The JSON request body of `method` on `path`. */
type RequestBody<P extends keyof paths, M extends keyof paths[P]> = paths[P][M] extends {
  requestBody?: infer B
}
  ? JsonContent<B>
  : never

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

export type AgentModel = Schemas['AgentVO']['model']

/** Chat options as stored and returned: all fields present. */
export type ChatOptions = Schemas['ChatOptions']

/** Chat options in requests: any field may be left out. */
export type ChatOptionsInput = Schemas['ChatOptionsInput']

export type ChatMessageRole = Schemas['ChatMessageVO']['role']

export type ToolVO = Schemas['ToolVO']
export type ToolType = ToolVO['type']

// ---------------------------------------------------------------------------
// Agent
// ---------------------------------------------------------------------------

export type Agent = Schemas['AgentVO']
export type CreateAgentRequest = RequestBody<'/api/agents', 'post'>
export type UpdateAgentRequest = RequestBody<'/api/agents/{agentId}', 'patch'>

export function listAgents() {
  return http.get<ResponseData<'/api/agents', 'get'>>('/agents')
}

export function createAgent(request: CreateAgentRequest) {
  return http.post<ResponseData<'/api/agents', 'post'>>('/agents', request)
}

export function updateAgent(agentId: string, request: UpdateAgentRequest) {
  return http.patch<ResponseData<'/api/agents/{agentId}', 'patch'>>(`/agents/${agentId}`, request)
}

// Delete endpoints always return data: null; the document can only say "no content type" (unknown).
export function deleteAgent(agentId: string) {
  return http.delete<null>(`/agents/${agentId}`)
}

// ---------------------------------------------------------------------------
// Chat session
// ---------------------------------------------------------------------------

export type ChatSession = Schemas['ChatSessionVO']
export type CreateChatSessionRequest = RequestBody<'/api/chat-sessions', 'post'>
/** Only the title can change; the agent is fixed when the session is created. */
export type UpdateChatSessionRequest = RequestBody<'/api/chat-sessions/{chatSessionId}', 'patch'>

export function listChatSessions() {
  return http.get<ResponseData<'/api/chat-sessions', 'get'>>('/chat-sessions')
}

export function getChatSession(chatSessionId: string) {
  return http.get<ResponseData<'/api/chat-sessions/{chatSessionId}', 'get'>>(
    `/chat-sessions/${chatSessionId}`,
  )
}

export function listChatSessionsByAgent(agentId: string) {
  return http.get<ResponseData<'/api/chat-sessions/agent/{agentId}', 'get'>>(
    `/chat-sessions/agent/${agentId}`,
  )
}

export function createChatSession(request: CreateChatSessionRequest) {
  return http.post<ResponseData<'/api/chat-sessions', 'post'>>('/chat-sessions', request)
}

export function updateChatSession(chatSessionId: string, request: UpdateChatSessionRequest) {
  return http.patch<ResponseData<'/api/chat-sessions/{chatSessionId}', 'patch'>>(
    `/chat-sessions/${chatSessionId}`,
    request,
  )
}

export function deleteChatSession(chatSessionId: string) {
  return http.delete<null>(`/chat-sessions/${chatSessionId}`)
}

// ---------------------------------------------------------------------------
// Chat message
// ---------------------------------------------------------------------------

export type ToolCallMetadata = Schemas['ToolCallMetadata']
export type ToolResponseMetadata = Schemas['ToolResponseMetadata']
/** Assistant messages that request tools carry toolCalls; tool messages carry toolResponse. */
export type ChatMessageMetadata = Schemas['ChatMessageMetadata']
export type ChatMessage = Schemas['ChatMessageVO']
/** Always a user message; assistant, tool, and system messages come only from the agent runtime. */
export type CreateChatMessageRequest = RequestBody<'/api/chat-messages', 'post'>

export function listChatMessagesBySession(sessionId: string) {
  return http.get<ResponseData<'/api/chat-messages/session/{sessionId}', 'get'>>(
    `/chat-messages/session/${sessionId}`,
  )
}

export function createChatMessage(request: CreateChatMessageRequest) {
  return http.post<ResponseData<'/api/chat-messages', 'post'>>('/chat-messages', request)
}

export function deleteChatMessage(chatMessageId: string) {
  return http.delete<null>(`/chat-messages/${chatMessageId}`)
}

// ---------------------------------------------------------------------------
// Knowledge base
// ---------------------------------------------------------------------------

export type KnowledgeBase = Schemas['KnowledgeBaseVO']
export type CreateKnowledgeBaseRequest = RequestBody<'/api/knowledge-bases', 'post'>
export type UpdateKnowledgeBaseRequest = RequestBody<
  '/api/knowledge-bases/{knowledgeBaseId}',
  'patch'
>

export function listKnowledgeBases() {
  return http.get<ResponseData<'/api/knowledge-bases', 'get'>>('/knowledge-bases')
}

export function createKnowledgeBase(request: CreateKnowledgeBaseRequest) {
  return http.post<ResponseData<'/api/knowledge-bases', 'post'>>('/knowledge-bases', request)
}

export function updateKnowledgeBase(knowledgeBaseId: string, request: UpdateKnowledgeBaseRequest) {
  return http.patch<ResponseData<'/api/knowledge-bases/{knowledgeBaseId}', 'patch'>>(
    `/knowledge-bases/${knowledgeBaseId}`,
    request,
  )
}

export function deleteKnowledgeBase(knowledgeBaseId: string) {
  return http.delete<null>(`/knowledge-bases/${knowledgeBaseId}`)
}

// ---------------------------------------------------------------------------
// Document
// ---------------------------------------------------------------------------

export type Document = Schemas['DocumentVO']
/** metadata is server-managed (it holds the stored file path), so requests cannot set it. */
export type CreateDocumentRequest = RequestBody<'/api/documents', 'post'>
export type UpdateDocumentRequest = RequestBody<'/api/documents/{documentId}', 'patch'>

export function listDocuments() {
  return http.get<ResponseData<'/api/documents', 'get'>>('/documents')
}

export function listDocumentsByKb(kbId: string) {
  return http.get<ResponseData<'/api/documents/kb/{kbId}', 'get'>>(`/documents/kb/${kbId}`)
}

export function createDocument(request: CreateDocumentRequest) {
  return http.post<ResponseData<'/api/documents', 'post'>>('/documents', request)
}

/** One Markdown file (.md / .markdown, UTF-8, at most 1 MB, at least one heading); returns the created document. */
export function uploadDocument(kbId: string, file: File) {
  const formData = new FormData()
  formData.append('kbId', kbId)
  formData.append('file', file)
  return http.postForm<ResponseData<'/api/documents/upload', 'post'>>('/documents/upload', formData)
}

export function updateDocument(documentId: string, request: UpdateDocumentRequest) {
  return http.patch<ResponseData<'/api/documents/{documentId}', 'patch'>>(
    `/documents/${documentId}`,
    request,
  )
}

export function deleteDocument(documentId: string) {
  return http.delete<null>(`/documents/${documentId}`)
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------

/** The tools an agent can choose for `allowedTools`; fixed tools are always given and not listed. */
export function listOptionalTools() {
  return http.get<ResponseData<'/api/tools', 'get'>>('/tools')
}

// ---------------------------------------------------------------------------
// SSE (not described by OpenAPI, so typed by hand)
// ---------------------------------------------------------------------------

export type SseMessageType =
  | 'AI_GENERATED_CONTENT'
  | 'AI_PLANNING'
  | 'AI_THINKING'
  | 'AI_EXECUTING'
  | 'AI_DONE'

export interface SseMessagePayload {
  message: ChatMessage | null
  statusText: string | null
  done: boolean
}

export interface SseMessageEvent {
  type: SseMessageType
  payload: SseMessagePayload
  metadata: {
    chatMessageId: string | null
  }
}

export function getSseUrl(chatSessionId: string): string {
  return `${BASE_URL.replace(/\/api$/, '')}/sse/connect/${chatSessionId}`
}
