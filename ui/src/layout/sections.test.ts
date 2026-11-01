import { describe, expect, it } from 'vitest'
import { sectionOf } from './sections'

describe('sectionOf', () => {
  it.each([
    ['/agent', 'agents'],
    ['/', 'chats'],
    ['/chat', 'chats'],
    ['/chat/5b0c', 'chats'],
    ['/chat/5b0c/', 'chats'],
    ['/knowledge-base', 'knowledgeBases'],
    ['/knowledge-base/7a1e', 'knowledgeBases'],
  ])('puts %s under %s', (path, section) => {
    expect(sectionOf(path)).toBe(section)
  })

  it.each(['/agents', '/chat/a/b', '/chatter', '/knowledge-bases', '/settings'])(
    'gives no section to %s',
    (path) => {
      expect(sectionOf(path)).toBeNull()
    },
  )
})
