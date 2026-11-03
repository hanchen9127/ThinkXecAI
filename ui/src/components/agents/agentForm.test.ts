import { describe, expect, it } from 'vitest'
import type { Agent } from '../../api/api'
import { draftFrom, emptyDraft, toCreateRequest, toUpdateRequest, validate } from './agentForm'

const agent: Agent = {
  id: 'a1',
  name: 'Store analyst',
  description: 'Answers questions about orders.',
  model: 'deepseek-chat',
  allowedTools: ['dataBaseTool'],
  allowedKbs: ['k1'],
  chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
  systemPrompt: null,
  createdAt: '2026-11-01T03:00:00',
  updatedAt: '2026-11-02T03:10:00',
}

describe('emptyDraft', () => {
  it('starts with the first model and the backend defaults', () => {
    expect(emptyDraft()).toMatchObject({
      model: 'deepseek-chat',
      chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
    })
  })
})

describe('validate', () => {
  it.each(['', '   '])('asks for a name when it is %j', (name) => {
    expect(validate({ ...emptyDraft(), name })).toEqual({ name: 'Enter a name.' })
  })

  it('accepts a named draft', () => {
    expect(validate({ ...emptyDraft(), name: 'Trip planner' })).toEqual({})
  })
})

describe('toCreateRequest', () => {
  it('trims text and leaves out empty description and instructions', () => {
    const request = toCreateRequest({ ...emptyDraft(), name: '  Trip planner ', description: ' ' })

    expect(request).toEqual({
      name: 'Trip planner',
      model: 'deepseek-chat',
      description: undefined,
      systemPrompt: undefined,
      allowedTools: [],
      allowedKbs: [],
      chatOptions: { temperature: 0.7, topP: 1, messageLength: 10 },
    })
  })
})

describe('toUpdateRequest', () => {
  it('sends nothing when nothing changed', () => {
    expect(toUpdateRequest(agent, draftFrom(agent))).toEqual({})
  })

  it('sends only the changed fields', () => {
    const draft = { ...draftFrom(agent), name: 'Shop analyst', model: 'qwen3.8-flash' as const }

    expect(toUpdateRequest(agent, draft)).toEqual({ name: 'Shop analyst', model: 'qwen3.8-flash' })
  })

  it('sends only the changed chat options', () => {
    const draft = draftFrom(agent)
    draft.chatOptions.messageLength = 20

    expect(toUpdateRequest(agent, draft)).toEqual({ chatOptions: { messageLength: 20 } })
  })

  it('sends a whole list when one entry changed, in its new order', () => {
    const draft = { ...draftFrom(agent), allowedTools: ['emailTool', 'dataBaseTool'] }

    expect(toUpdateRequest(agent, draft)).toEqual({ allowedTools: ['emailTool', 'dataBaseTool'] })
  })

  it('clears a description with an empty string and adds instructions', () => {
    const draft = { ...draftFrom(agent), description: '', systemPrompt: 'Be brief.' }

    expect(toUpdateRequest(agent, draft)).toEqual({ description: '', systemPrompt: 'Be brief.' })
  })

  it('treats a null description and an empty one as the same', () => {
    const blank = { ...agent, description: null }

    expect(toUpdateRequest(blank, draftFrom(blank))).toEqual({})
  })
})
