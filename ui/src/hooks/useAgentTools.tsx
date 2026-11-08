import Tag from '../components/Tag'
import { useTools } from './useTools'

/** The tools an agent has: the ones it chose, then the ones every agent has. */
export function agentTools(allowedTools: string[], fixed: string[]) {
  return [
    ...allowedTools.map((name) => ({ name, always: false })),
    ...fixed.map((name) => ({ name, always: true })),
  ]
}

/** `agentTools` with the shared tool list: a count and the labelled tags for one agent. */
export function useAgentTools(allowedTools: string[]) {
  const { tools, labelOf } = useTools()
  const fixed = tools.filter((tool) => tool.type === 'FIXED').map((tool) => tool.name)
  const all = agentTools(allowedTools, fixed)
  return {
    count: all.length,
    tags: all.map(({ name, always }, i) => (
      <Tag key={`${name}-${i}`} kind="tool" always={always}>
        {labelOf(name)}
      </Tag>
    )),
  }
}
