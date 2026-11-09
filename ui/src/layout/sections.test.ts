import { describe, expect, it } from 'vitest'
import { isListRoute, sectionOf } from './sections'

describe('sectionOf', () => {
  it.each([
    ['/agent', 'agents'],
    ['/agent/a1', 'agents'],
    ['/', 'chats'],
    ['/chat', 'chats'],
    ['/chat/5b0c', 'chats'],
    ['/chat/new', 'chats'],
    ['/chat/5b0c/', 'chats'],
    ['/knowledge-base', 'knowledgeBases'],
    ['/knowledge-base/7a1e', 'knowledgeBases'],
  ])('puts %s under %s', (path, section) => {
    expect(sectionOf(path)).toBe(section)
  })

  it.each(['/agents', '/agent/a/b', '/chat/a/b', '/chatter', '/knowledge-bases', '/settings'])(
    'gives no section to %s',
    (path) => {
      expect(sectionOf(path)).toBeNull()
    },
  )
})

describe('isListRoute', () => {
  it.each(['/agent', '/chat', '/chat/', '/knowledge-base'])(
    'shows the list at %s on a narrow screen',
    (path) => {
      expect(isListRoute(path)).toBe(true)
    },
  )

  it.each(['/', '/chat/new', '/chat/5b0c', '/agent/a1', '/knowledge-base/7a1e', '/settings'])(
    'shows the page at %s on a narrow screen',
    (path) => {
      expect(isListRoute(path)).toBe(false)
    },
  )
})
