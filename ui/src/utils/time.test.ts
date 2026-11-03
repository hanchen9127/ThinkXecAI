import { describe, expect, it } from 'vitest'
import { formatApiTimestamp, parseApiTimestamp, formatListTime } from './time'

// Two-digit fields and a 24-hour clock, so the expected strings do not depend on ICU's spacing.
const LOCAL: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
}

describe('test time zone', () => {
  it('is Australia/Sydney, so a UTC mistake cannot pass by accident', () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe('Australia/Sydney')
  })
})

describe('parseApiTimestamp', () => {
  it('reads a timestamp without an offset as UTC', () => {
    expect(parseApiTimestamp('2026-10-17T03:15:00').toISOString()).toBe('2026-10-17T03:15:00.000Z')
  })

  it('accepts a missing seconds field', () => {
    expect(parseApiTimestamp('2026-10-17T03:15').toISOString()).toBe('2026-10-17T03:15:00.000Z')
  })

  it.each([
    ['2026-10-17T03:15:00.1', '2026-10-17T03:15:00.100Z'],
    ['2026-10-17T03:15:00.123456', '2026-10-17T03:15:00.123Z'],
    ['2026-10-17T03:15:00.999999999', '2026-10-17T03:15:00.999Z'],
  ])('keeps the milliseconds of %s', (value, expected) => {
    expect(parseApiTimestamp(value).toISOString()).toBe(expected)
  })

  it.each([
    '2026-10-17T03:15:00Z',
    '2026-10-17T03:15:00+11:00',
    '2026-10-17 03:15:00',
    '2026-10-17',
    '2026-13-01T00:00:00',
    '2026-02-30T00:00:00',
    '2026-10-17T25:00:00',
    '2026-10-17T03:60:00',
    '2026-10-17T03:15:60',
    '',
  ])('refuses %j', (value) => {
    expect(() => parseApiTimestamp(value)).toThrow(RangeError)
  })

  it('keeps the order of two messages across the end of daylight saving time', () => {
    // Sydney leaves daylight saving time at 03:00 on 2026-04-05; both instants are 02:30 there.
    const first = parseApiTimestamp('2026-04-04T15:30:00')
    const second = parseApiTimestamp('2026-04-04T16:30:00')

    expect(second.getTime() - first.getTime()).toBe(60 * 60 * 1000)
    expect(formatApiTimestamp('2026-04-04T15:30:00', LOCAL)).toBe('04/05/2026, 02:30')
    expect(formatApiTimestamp('2026-04-04T16:30:00', LOCAL)).toBe('04/05/2026, 02:30')
  })
})

describe('formatApiTimestamp', () => {
  it('shows the time in the browser time zone', () => {
    expect(formatApiTimestamp('2026-10-17T03:15:00', LOCAL)).toBe('10/17/2026, 14:15')
  })

  it('formats with a medium date and a short time by default', () => {
    expect(formatApiTimestamp('2026-10-17T03:15:00')).toMatch(/^Oct 17, 2026, 2:15\sPM$/)
  })

  it('returns a value that is not an API timestamp unchanged', () => {
    expect(formatApiTimestamp('yesterday')).toBe('yesterday')
  })
})

describe('formatListTime', () => {
  // 2 Nov 2026, 10:00 in Sydney (UTC+11).
  const now = new Date('2026-11-01T23:00:00Z')

  it('shows the time for today, counted in the browser time zone', () => {
    // 1 Nov 14:00 UTC is 2 Nov 01:00 in Sydney: today, though yesterday in UTC.
    expect(formatListTime('2026-11-01T14:00:00', now)).toBe('1:00 AM')
  })

  it('says Yesterday for the day before', () => {
    expect(formatListTime('2026-11-01T12:59:00', now)).toBe('Yesterday')
    expect(formatListTime('2026-10-31T13:00:00', now)).toBe('Yesterday')
  })

  it('shows the date for earlier days this year, and the year before that', () => {
    expect(formatListTime('2026-10-31T12:59:00', now)).toBe('Oct 31')
    expect(formatListTime('2025-12-30T00:00:00', now)).toBe('Dec 30, 2025')
  })

  it('returns a value that is not an API timestamp unchanged', () => {
    expect(formatListTime('soon', now)).toBe('soon')
  })
})
