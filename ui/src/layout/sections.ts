export type Section = 'agents' | 'chats' | 'knowledgeBases'

/** The side-menu section a path belongs to; `null` for a path no route serves. */
export function sectionOf(pathname: string): Section | null {
  const path = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  if (path === '/agent') return 'agents'
  if (path === '/' || path === '/chat' || /^\/chat\/[^/]+$/.test(path)) return 'chats'
  if (path === '/knowledge-base' || /^\/knowledge-base\/[^/]+$/.test(path)) return 'knowledgeBases'
  return null
}
