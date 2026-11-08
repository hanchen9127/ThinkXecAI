import type { ReactNode } from 'react'
import { useToolsState } from '../hooks/useTools'
import { ToolsContext } from './toolsContext'

/** One tool list, loaded once, for every place that names a tool: lists, the form, and the trace. */
export default function ToolsProvider({ children }: { children: ReactNode }) {
  return <ToolsContext.Provider value={useToolsState()}>{children}</ToolsContext.Provider>
}
