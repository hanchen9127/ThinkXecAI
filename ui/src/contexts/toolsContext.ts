import { createContext } from 'react'
import type { ToolVO } from '../api/api'

export interface ToolsValue {
  /** Every tool people are shown, fixed first (ADR 065). */
  tools: ToolVO[]
  loading: boolean
  error: unknown
  reload: () => void
}

/** Outside `ToolsProvider` there are no tools, so every tool is named by its code name. */
export const ToolsContext = createContext<ToolsValue>({
  tools: [],
  loading: false,
  error: undefined,
  reload: () => {},
})
