export type Section = 'agents' | 'chats' | 'knowledgeBases'

/** Each section's list route and its name in "Back to …". */
export const LISTS: Record<Section, { to: string; name: string }> = {
  agents: { to: '/agent', name: 'agents' },
  chats: { to: '/chat', name: 'chats' },
  knowledgeBases: { to: '/knowledge-base', name: 'knowledge bases' },
}

function trimmed(pathname: string) {
  return pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
}

/** The side-menu section a path belongs to; `null` for a path no route serves. */
export function sectionOf(pathname: string): Section | null {
  const path = trimmed(pathname)
  if (path === '/agent' || /^\/agent\/[^/]+$/.test(path)) return 'agents'
  if (path === '/' || path === '/chat' || /^\/chat\/[^/]+$/.test(path)) return 'chats'
  if (path === '/knowledge-base' || /^\/knowledge-base\/[^/]+$/.test(path)) return 'knowledgeBases'
  return null
}

/**
 * Whether a narrow screen shows the section's list here rather than a page (ADR 063): a section's
 * own route does, everything else is an item. A new chat is `/chat/new` (or `/`), so `/chat` can be
 * the list on a narrow screen while it still opens a new chat beside the list on a wide one.
 */
export function isListRoute(pathname: string): boolean {
  const path = trimmed(pathname)
  return Object.values(LISTS).some((list) => list.to === path)
}
