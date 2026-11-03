// Compile-time checks, run by `npm run typecheck` (never imported, so never bundled). They pin the
// types derived from schema.d.ts, so a broken helper cannot silently turn them into `never` or `any`.
import type {
  AGENT_MODELS,
  Agent,
  AgentModel,
  ChatMessage,
  ChatMessageMetadata,
  ChatOptionsInput,
  CreateAgentRequest,
  CreateChatMessageRequest,
  Document,
  ToolType,
  ToolVO,
  UpdateChatSessionRequest,
  createAgent,
  deleteAgent,
  listAgents,
  listChatMessagesBySession,
  listOptionalTools,
  uploadDocument,
} from './api'

type Equal<A, B> =
  (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false
type Expect<T extends true> = T
type Data<F extends (...args: never[]) => Promise<unknown>> = Awaited<ReturnType<F>>

export type ApiTypeChecks = [
  Expect<Equal<Data<typeof listAgents>, Agent[]>>,
  Expect<Equal<Data<typeof createAgent>, Agent>>,
  Expect<Equal<Data<typeof deleteAgent>, null>>,
  Expect<Equal<Data<typeof listChatMessagesBySession>, ChatMessage[]>>,
  Expect<Equal<Data<typeof uploadDocument>, Document>>,
  Expect<Equal<Data<typeof listOptionalTools>, ToolVO[]>>,
  Expect<Equal<ToolType, 'FIXED' | 'OPTIONAL'>>,
  Expect<Equal<keyof ToolVO, 'name' | 'description' | 'type'>>,
  Expect<Equal<AgentModel, 'deepseek-chat' | 'gemini-3.8-flash' | 'qwen3.8-flash'>>,
  // A model added to the backend fails here until the picker lists it.
  Expect<Equal<(typeof AGENT_MODELS)[number], AgentModel>>,
  Expect<Equal<Agent['id'], string>>,
  Expect<Equal<Agent['description'], string | null>>,
  Expect<Equal<ChatMessage['metadata'], ChatMessageMetadata | null>>,
  Expect<Equal<keyof ChatMessageMetadata, 'toolCalls' | 'toolResponse'>>,
  Expect<Equal<CreateAgentRequest['chatOptions'], ChatOptionsInput | undefined>>,
  Expect<Equal<ChatOptionsInput['temperature'], number | undefined>>,
  Expect<Equal<keyof UpdateChatSessionRequest, 'title'>>,
  Expect<Equal<CreateChatMessageRequest, { sessionId: string; content: string }>>,
]
