import TabHeading from './TabHeading'

// The agent list and its actions arrive with agent management (Phase 13 commit 6).
export default function AgentTabContent() {
  return (
    <section aria-label="Agents" className="flex flex-col gap-5">
      <TabHeading title="Agents" />
    </section>
  )
}
