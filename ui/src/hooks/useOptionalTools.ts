import { listOptionalTools } from '../api/api'
import { useRemoteData } from './useRemoteData'

/** The tools an agent can choose; fixed tools are always given and not listed. */
export function useOptionalTools() {
  const { data, loading, error, reload } = useRemoteData(listOptionalTools)
  return { tools: data ?? [], loading, error, reload }
}
