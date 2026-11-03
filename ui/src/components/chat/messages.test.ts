import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../../api/api'
import {
  chatTitle,
  decodeToolResult,
  formatArguments,
  formatDuration,
  groupHistory,
  summarizeResult,
  titleFrom,
} from './messages'

let clock = 0
function message(
  role: ChatMessage['role'],
  content: string | null,
  metadata: ChatMessage['metadata'] = null,
): ChatMessage {
  clock += 1
  const at = `2026-11-02T03:00:${String(clock).padStart(2, '0')}`
  return { id: `m${clock}`, sessionId: 's1', role, content, metadata, createdAt: at, updatedAt: at }
}
const call = (id: string, name: string, args = '{}') => ({
  id,
  type: 'function',
  name,
  arguments: args,
})
const result = (id: string, name: string, data: string) =>
  message('tool', null, { toolResponse: { id, name, responseData: data } })

describe('groupHistory', () => {
  it('puts everything between two user messages into one run of steps and answers', () => {
    clock = 0
    const question = message('user', 'Which phones are out of stock?')
    const step1 = message('assistant', 'I will look it up.', {
      toolCalls: [call('c1', 'dataBaseTool')],
    })
    const result1 = result('c1', 'dataBaseTool', '"| sku |"')
    const step2 = message('assistant', '', {
      toolCalls: [call('c2', 'knowledgeSearch'), call('c3', 'dateTool')],
    })
    const result3 = result('c3', 'dateTool', '"2026-11-02"')
    const answer = message('assistant', 'One phone is out of stock.')
    const followUp = message('user', 'Email it to me.')

    const items = groupHistory([question, step1, result1, step2, result3, answer, followUp])

    expect(items.map((i) => i.kind)).toEqual(['user', 'agent', 'user'])
    const run = items[1]
    if (run.kind !== 'agent') throw new Error('expected a run')
    // One second between messages: a Think counts from the message before it, a call to its result.
    expect(run.steps).toEqual([
      {
        id: step1.id,
        think: 'I will look it up.',
        thinkMs: 1000,
        calls: [{ call: call('c1', 'dataBaseTool'), result: '"| sku |"', ms: 1000 }],
      },
      {
        id: step2.id,
        think: null,
        thinkMs: 1000,
        calls: [
          { call: call('c2', 'knowledgeSearch'), result: undefined, ms: undefined },
          { call: call('c3', 'dateTool'), result: '"2026-11-02"', ms: 1000 },
        ],
      },
    ])
    expect(run.answers).toEqual([answer])
    expect(run.ms).toBe(5000)
  })

  it('orders by creation time and leaves out system messages and empty answers', () => {
    clock = 0
    const question = message('user', 'Hi')
    const system = message('system', 'You are helpful.')
    const empty = message('assistant', '  ')
    const answer = message('assistant', 'Hello!')

    const items = groupHistory([answer, empty, system, question])

    expect(items).toEqual([
      { kind: 'user', message: question },
      { kind: 'agent', id: empty.id, steps: [], answers: [answer], ms: 3000 },
    ])
  })

  it('keeps a notice that ends a run as one of its answers (ADR 043)', () => {
    clock = 0
    const question = message('user', 'Go')
    const step = message('assistant', null, { toolCalls: [call('c1', 'weatherTool')] })
    const notice = message('assistant', 'The model failed or timed out.')

    const [, run] = groupHistory([question, step, notice])
    expect(run.kind === 'agent' && run.answers).toEqual([notice])
  })
})

describe('summarizeResult', () => {
  it('counts the rows of a table, ignoring the row note under it', () => {
    const table = '| sku |\n| --- |\n| SP-1 |\n| SP-2 |\n\n(2 rows)'
    expect(summarizeResult(JSON.stringify(table))).toBe('2 rows')
    expect(summarizeResult(JSON.stringify('| sku |\n| --- |\n| SP-1 |\n'))).toBe('1 row')
  })

  it('counts a list, and shows an object or text by its start', () => {
    expect(summarizeResult('[1,2,3]')).toBe('3 items')
    expect(summarizeResult('{"city": "Sydney"}')).toBe('{"city":"Sydney"}')
    expect(summarizeResult(JSON.stringify('\nReturns within 30 days.\nMore text.'))).toBe(
      'Returns within 30 days.',
    )
    expect(summarizeResult('x'.repeat(100))).toBe(`${'x'.repeat(80)}…`)
  })
})

describe('formatDuration', () => {
  it('shows seconds with one decimal, minutes from 60 s', () => {
    expect(formatDuration(400)).toBe('0.4 s')
    expect(formatDuration(12_000)).toBe('12.0 s')
    expect(formatDuration(65_000)).toBe('1 min 5 s')
  })
})

describe('decodeToolResult', () => {
  it('decodes one JSON level: text is shown as Markdown', () => {
    expect(decodeToolResult('"| sku |\\n|---|\\n| SP-1 |"')).toEqual({
      kind: 'markdown',
      text: '| sku |\n|---|\n| SP-1 |',
    })
  })

  it('shows anything else as indented JSON', () => {
    expect(decodeToolResult('{"city":"Sydney","temperature":21}')).toEqual({
      kind: 'json',
      text: '{\n  "city": "Sydney",\n  "temperature": 21\n}',
    })
  })

  it('shows a result that is not JSON as it was stored', () => {
    expect(decodeToolResult('Tool failed: timeout')).toEqual({
      kind: 'raw',
      text: 'Tool failed: timeout',
    })
  })
})

describe('formatArguments', () => {
  it('lists each argument, strings as written', () => {
    expect(formatArguments('{"sql":"SELECT 1;","limit":5}')).toBe('sql: SELECT 1;\nlimit: 5')
  })

  it('says when there are none, and keeps anything else as it is', () => {
    expect(formatArguments('{}')).toBe('no arguments')
    expect(formatArguments('[1,2]')).toBe('[1,2]')
    expect(formatArguments('not json')).toBe('not json')
  })
})

describe('titleFrom', () => {
  it('uses the first line, spaces collapsed', () => {
    expect(titleFrom('  Which   phones?\nAnd when?')).toBe('Which phones?')
  })

  it('cuts a long line at a word, near 60 characters', () => {
    const long = 'Which of our phones are out of stock this week, and when does each one come back?'
    const title = titleFrom(long)
    expect(title).toBe('Which of our phones are out of stock this week, and when…')
    expect(title.length).toBeLessThanOrEqual(61)
  })

  it('cuts a long word where it must', () => {
    expect(titleFrom('x'.repeat(80))).toBe(`${'x'.repeat(60)}…`)
  })
})

describe('chatTitle', () => {
  it('names a chat without a title', () => {
    expect(chatTitle({ title: null })).toBe('Untitled chat')
    expect(chatTitle({ title: ' ' })).toBe('Untitled chat')
    expect(chatTitle({ title: 'Stock' })).toBe('Stock')
  })
})
