import { describe, expect, it } from 'vitest'
import { sectionOf } from './sections'

describe('sectionOf', () => {
  it.each([
    ['/agent', 'agents'],
    ['/agent/a1', 'agents'],
    ['/', 'chats'],
    ['/chat', 'chats'],
    ['/chat/5b0c', 'chats'],
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
