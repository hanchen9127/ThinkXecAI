import { BASE_URL, http } from './http'

// ---------------------------------------------------------------------------
// Shared types
// ---------------------------------------------------------------------------

export type AgentModel = 'deepseek-chat' | 'gpt-5.4-mini'

export interface ChatOptions {
  temperature: number
  topP: number
  messageLength: number
}

export type ChatMessageRole = 'user' | 'assistant' | 'system' | 'tool'

export type ToolType = 'FIXED' | 'OPTIONAL'

export interface ToolVO {
  name: string
  description: string
  type: ToolType
}

// ---------------------------------------------------------------------------
// Agent
// ---------------------------------------------------------------------------

export interface Agent {
  id: string
  name: string
  description: string | null
  systemPrompt: string | null
  model: AgentModel
  allowedTools: string[]
  allowedKbs: string[]
  chatOptions: ChatOptions
  createdAt: string
  updatedAt: string
}

export interface CreateAgentRequest {
  name: string
  description?: string | null
  systemPrompt?: string | null
  model: AgentModel
  allowedTools?: string[]
  allowedKbs?: string[]
  chatOptions?: Partial<ChatOptions>
}

export type UpdateAgentRequest = Partial<CreateAgentRequest>

export function listAgents() {
  return http.get<Agent[]>('/agents')
}

export function createAgent(request: CreateAgentRequest) {
  return http.post<Agent>('/agents', request)
}

export function updateAgent(agentId: string, request: UpdateAgentRequest) {
  return http.patch<Agent>(`/agents/${agentId}`, request)
}

export function deleteAgent(agentId: string) {
  return http.delete<null>(`/agents/${agentId}`)
}

// ---------------------------------------------------------------------------
// Chat session
// ---------------------------------------------------------------------------

export interface ChatSession {
  id: string
  agentId: string | null
  title: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateChatSessionRequest {
  agentId?: string | null
  title?: string | null
}

export type UpdateChatSessionRequest = Partial<CreateChatSessionRequest>

export function listChatSessions() {
  return http.get<ChatSession[]>('/chat-sessions')
}

export function getChatSession(chatSessionId: string) {
  return http.get<ChatSession>(`/chat-sessions/${chatSessionId}`)
}

export function listChatSessionsByAgent(agentId: string) {
  return http.get<ChatSession[]>(`/chat-sessions/agent/${agentId}`)
}

export function createChatSession(request: CreateChatSessionRequest) {
  return http.post<ChatSession>('/chat-sessions', request)
}

export function updateChatSession(chatSessionId: string, request: UpdateChatSessionRequest) {
  return http.patch<ChatSession>(`/chat-sessions/${chatSessionId}`, request)
}

export function deleteChatSession(chatSessionId: string) {
  return http.delete<null>(`/chat-sessions/${chatSessionId}`)
}

// ---------------------------------------------------------------------------
// Chat message
// ---------------------------------------------------------------------------

export interface ToolCallMetadata {
  id: string
  type: string
  name: string
  /** Raw JSON argument string, as produced by the model. */
  arguments: string
}

export interface ToolResponseMetadata {
  /** Matches the id of the ToolCallMetadata this response answers. */
  id: string
  name: string
  responseData: string
}

/** Assistant messages that request tools carry toolCalls; tool messages carry toolResponse. */
export interface ChatMessageMetadata {
  toolCalls?: ToolCallMetadata[]
  toolResponse?: ToolResponseMetadata
}

export interface ChatMessage {
  id: string
  sessionId: string
  role: ChatMessageRole
  content: string | null
  metadata: ChatMessageMetadata | null
  createdAt: string
  updatedAt: string
}

export interface CreateChatMessageRequest {
  sessionId: string
  role: ChatMessageRole
  content?: string | null
  metadata?: ChatMessageMetadata | null
}

export interface UpdateChatMessageRequest {
  content?: string | null
  metadata?: ChatMessageMetadata | null
}

export function listChatMessagesBySession(sessionId: string) {
  return http.get<ChatMessage[]>(`/chat-messages/session/${sessionId}`)
}

export function createChatMessage(request: CreateChatMessageRequest) {
  return http.post<ChatMessage>('/chat-messages', request)
}

export function updateChatMessage(chatMessageId: string, request: UpdateChatMessageRequest) {
  return http.patch<ChatMessage>(`/chat-messages/${chatMessageId}`, request)
}

export function deleteChatMessage(chatMessageId: string) {
  return http.delete<null>(`/chat-messages/${chatMessageId}`)
}

// ---------------------------------------------------------------------------
// Knowledge base
// ---------------------------------------------------------------------------

export interface KnowledgeBase {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string | null
}

export type UpdateKnowledgeBaseRequest = Partial<CreateKnowledgeBaseRequest>

export function listKnowledgeBases() {
  return http.get<KnowledgeBase[]>('/knowledge-bases')
}

export function createKnowledgeBase(request: CreateKnowledgeBaseRequest) {
  return http.post<KnowledgeBase>('/knowledge-bases', request)
}

export function updateKnowledgeBase(knowledgeBaseId: string, request: UpdateKnowledgeBaseRequest) {
  return http.patch<KnowledgeBase>(`/knowledge-bases/${knowledgeBaseId}`, request)
}

export function deleteKnowledgeBase(knowledgeBaseId: string) {
  return http.delete<null>(`/knowledge-bases/${knowledgeBaseId}`)
}

// ---------------------------------------------------------------------------
// Document
// ---------------------------------------------------------------------------

export interface Document {
  id: string
  kbId: string
  name: string
  metadata: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface CreateDocumentRequest {
  kbId: string
  name: string
  metadata?: Record<string, unknown> | null
}

export interface UpdateDocumentRequest {
  name?: string
  metadata?: Record<string, unknown> | null
}

export function listDocuments() {
  return http.get<Document[]>('/documents')
}

export function listDocumentsByKb(kbId: string) {
  return http.get<Document[]>(`/documents/kb/${kbId}`)
}

export function createDocument(request: CreateDocumentRequest) {
  return http.post<Document>('/documents', request)
}

export function uploadDocument(kbId: string, file: File) {
  const formData = new FormData()
  formData.append('kbId', kbId)
  formData.append('file', file)
  return http.postForm<Document>('/documents/upload', formData)
}

export function updateDocument(documentId: string, request: UpdateDocumentRequest) {
  return http.patch<Document>(`/documents/${documentId}`, request)
}

export function deleteDocument(documentId: string) {
  return http.delete<null>(`/documents/${documentId}`)
}

// ---------------------------------------------------------------------------
// Tools
// ---------------------------------------------------------------------------

export function listOptionalTools() {
  return http.get<ToolVO[]>('/tools')
}

// ---------------------------------------------------------------------------
// SSE
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
