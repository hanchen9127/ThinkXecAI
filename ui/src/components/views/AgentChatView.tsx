import { useParams } from 'react-router'

// History, input, and live messages arrive in Phase 13 commits 8–10.
export default function AgentChatView() {
  const { chatSessionId } = useParams()
  return (
    <div className="px-12 py-9">
      <h1 className="m-0 text-xl font-semibold">{chatSessionId ? 'Chat' : 'New chat'}</h1>
    </div>
  )
}
