import TabHeading from './TabHeading'

// The chat list arrives with the chat view (Phase 13 commit 8).
export default function ChatTabContent() {
  return (
    <section aria-label="Chats" className="flex flex-col gap-5">
      <TabHeading title="Chats" />
    </section>
  )
}
