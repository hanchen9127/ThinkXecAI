// Compile-time checks, run by `npm run typecheck` (never imported, so never bundled). They pin the
// types derived from schema.d.ts, so a broken helper cannot silently turn them into `never` or `any`.
import type {
  Agent,
  AgentModel,
  ChatMessage,
  ChatMessageMetadata,
  ChatOptionsInput,
  CreateAgentRequest,
  UpdateChatSessionRequest,
  createAgent,
  deleteAgent,
  listAgents,
  listChatMessagesBySession,
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
  Expect<Equal<AgentModel, 'deepseek-chat' | 'gemini-3.8-flash'>>,
  Expect<Equal<Agent['id'], string>>,
  Expect<Equal<Agent['description'], string | null>>,
  Expect<Equal<ChatMessage['metadata'], ChatMessageMetadata | null>>,
  Expect<Equal<CreateAgentRequest['chatOptions'], ChatOptionsInput | undefined>>,
  Expect<Equal<ChatOptionsInput['temperature'], number | undefined>>,
  Expect<Equal<keyof UpdateChatSessionRequest, 'title'>>,
]
