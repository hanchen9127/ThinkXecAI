import { useCallback, useContext } from 'react'
import { type ToolVO, listTools } from '../api/api'
import { ToolsContext, type ToolsValue } from '../contexts/toolsContext'
import { useRemoteData } from './useRemoteData'

/** Loads the tools for `ToolsProvider`. */
export function useToolsState(): ToolsValue {
  const { data, loading, error, reload } = useRemoteData(listTools)
  return { tools: data ?? [], loading, error, reload }
}

/** The shared tools, plus `labelOf`, which names a tool by its stored name or the name a call used. */
export function useTools() {
  const value = useContext(ToolsContext)
  const { tools } = value
  const labelOf = useCallback((name: string) => toolLabel(tools, name), [tools])
  return { ...value, labelOf }
}

/** A tool's label (ADR 065); its code name while the tools are loading, failed, or it is not listed. */
export function toolLabel(tools: ToolVO[], name: string) {
  return tools.find((tool) => tool.name === name || tool.callName === name)?.label ?? name
}
