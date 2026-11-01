import TabHeading from './TabHeading'

// The knowledge-base list arrives with knowledge-base management (Phase 13 commit 7).
export default function KnowledgeBaseTabContent() {
  return (
    <section aria-label="Knowledge bases" className="flex flex-col gap-5">
      <TabHeading title="Knowledge bases" />
    </section>
  )
}
