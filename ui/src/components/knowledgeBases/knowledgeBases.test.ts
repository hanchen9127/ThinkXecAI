import { describe, expect, it } from 'vitest'
import type { Agent, KnowledgeBase } from '../../api/api'
import {
  LIMITS,
  agentsUsing,
  fileProblem,
  toCreateRequest,
  toUpdateRequest,
  validate,
} from './knowledgeBases'

const storePolicies: KnowledgeBase = {
  id: 'k1',
  name: 'Store policies',
  description: 'Returns and delivery',
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-01T03:00:00',
}

function file(name: string, size: number) {
  return new File([new Uint8Array(size)], name)
}

describe('fileProblem', () => {
  it.each(['notes.md', 'notes.markdown', 'NOTES.MD', 'Notes.Markdown'])('accepts %s', (name) => {
    expect(fileProblem(file(name, 10))).toBeNull()
  })

  it.each(['notes.txt', 'notes.md.txt', 'notes', 'md'])('refuses %s', (name) => {
    expect(fileProblem(file(name, 10))).toBe('only .md or .markdown files.')
  })

  it('accepts exactly 1 MB and refuses one byte more', () => {
    expect(fileProblem(file('big.md', LIMITS.uploadBytes))).toBeNull()
    expect(fileProblem(file('big.md', LIMITS.uploadBytes + 1))).toBe('larger than 1 MB.')
  })

  it('refuses an empty file', () => {
    expect(fileProblem(file('empty.md', 0))).toBe('the file is empty.')
  })

  it('refuses a name longer than 200 characters', () => {
    expect(fileProblem(file(`${'a'.repeat(197)}.md`, 10))).toBeNull()
    expect(fileProblem(file(`${'a'.repeat(198)}.md`, 10))).toBe(
      'the file name is longer than 200 characters.',
    )
  })
})

describe('the knowledge-base form', () => {
  it('asks for a name', () => {
    expect(validate({ name: '  ', description: '' })).toEqual({ name: 'Enter a name.' })
    expect(validate({ name: 'Manuals', description: '' })).toEqual({})
  })

  it('trims, and leaves out an empty description when creating', () => {
    expect(toCreateRequest({ name: ' Manuals ', description: '  ' })).toEqual({
      name: 'Manuals',
      description: undefined,
    })
    expect(toCreateRequest({ name: 'Manuals', description: ' Guides ' }).description).toBe('Guides')
  })

  it('sends only what changed when editing', () => {
    expect(
      toUpdateRequest(storePolicies, {
        name: 'Store policies ',
        description: 'Returns and delivery',
      }),
    ).toEqual({})
    expect(
      toUpdateRequest(storePolicies, { name: 'Policies', description: 'Returns and delivery' }),
    ).toEqual({
      name: 'Policies',
    })
    expect(toUpdateRequest(storePolicies, { name: 'Store policies', description: '' })).toEqual({
      description: '',
    })
  })

  it('treats a missing description as empty', () => {
    const bare = { ...storePolicies, description: null }
    expect(toUpdateRequest(bare, { name: 'Store policies', description: '' })).toEqual({})
  })
})

describe('agentsUsing', () => {
  it('lists the agents that search the knowledge base, in their order', () => {
    const agent = (id: string, allowedKbs: string[]) => ({ id, allowedKbs }) as Agent
    const agents = [agent('a1', ['k2']), agent('a2', ['k1', 'k2']), agent('a3', ['k1'])]
    expect(agentsUsing(agents, 'k1').map((a) => a.id)).toEqual(['a2', 'a3'])
    expect(agentsUsing(agents, 'k9')).toEqual([])
  })
})
