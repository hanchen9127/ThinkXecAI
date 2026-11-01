import { useParams } from 'react-router'

// Documents and upload arrive with knowledge-base management (Phase 13 commit 7).
export default function KnowledgeBaseView() {
  const { knowledgeBaseId } = useParams()
  return (
    <div className="px-12 py-9">
      <h1 className="m-0 text-xl font-semibold">
        {knowledgeBaseId ? 'Knowledge base' : 'Knowledge bases'}
      </h1>
    </div>
  )
}
