import Tag from '../components/Tag'
import { useTools } from './useTools'

/** The tools an agent has: the ones it chose, then the ones every agent has. */
function agentTools(allowedTools: string[], fixed: string[]) {
  return [
    ...allowedTools.map((name) => ({ name, always: false })),
    ...fixed.map((name) => ({ name, always: true })),
  ]
}

/**
 * `agentTools` with the shared tool list: a count and the labelled tags for one agent. `known` is false
 * while the list loads or after it failed: the tools every agent has are not known then, so a count
 * would be wrong (`error` and `reload` let the caller say so).
 */
export function useAgentTools(allowedTools: string[]) {
  const { tools, loading, error, reload, labelOf } = useTools()
  const fixed = tools.filter((tool) => tool.type === 'FIXED').map((tool) => tool.name)
  const all = agentTools(allowedTools, fixed)
  return {
    known: !loading && error === undefined,
    error,
    reload,
    count: all.length,
    tags: all.map(({ name, always }, i) => (
      <Tag key={`${name}-${i}`} kind="tool" always={always}>
        {labelOf(name)}
      </Tag>
    )),
  }
}
