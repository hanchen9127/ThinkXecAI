// API timestamps are UTC wall-clock time without an offset (`2026-10-17T03:15:00`, ADR 034). The
// fraction has 0–9 digits (Java's ISO format drops trailing zeros); `Date` keeps milliseconds.
const API_TIMESTAMP = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/

/** Reads an API timestamp as UTC. Throws a `RangeError` for anything else, including an offset. */
export function parseApiTimestamp(value: string): Date {
  const match = API_TIMESTAMP.exec(value)
  if (!match) throw new RangeError(`Not an API timestamp: ${value}`)
  const [, year, month, day, hour, minute, second = '0', fraction = ''] = match
  const millis = Number(fraction.padEnd(3, '0').slice(0, 3))
  const date = new Date(Date.UTC(+year, +month - 1, +day, +hour, +minute, +second, millis))
  // Date.UTC rolls out-of-range fields over (month 13, February 30, second 60) instead of failing,
  // so every field must read back as written.
  if (
    date.getUTCMonth() !== +month - 1 ||
    date.getUTCDate() !== +day ||
    date.getUTCHours() !== +hour ||
    date.getUTCMinutes() !== +minute ||
    date.getUTCSeconds() !== +second
  ) {
    throw new RangeError(`Not an API timestamp: ${value}`)
  }
  return date
}

const DEFAULT_FORMAT: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' }

/**
 * Formats an API timestamp in the browser's time zone. A value that is not an API timestamp is
 * returned unchanged, so one bad field cannot break a page.
 */
export function formatApiTimestamp(
  value: string,
  options: Intl.DateTimeFormatOptions = DEFAULT_FORMAT,
): string {
  try {
    return new Intl.DateTimeFormat('en', options).format(parseApiTimestamp(value))
  } catch {
    return value
  }
}

/**
 * A short time for list items (V1 element 5): the time today, "Yesterday", the date this year,
 * otherwise the date with the year. Days are counted in the browser's time zone.
 */
export function formatListTime(value: string, now: Date = new Date()): string {
  let date: Date
  try {
    date = parseApiTimestamp(value)
  } catch {
    return value
  }
  const day = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (day(date) === day(now)) return formatApiTimestamp(value, { timeStyle: 'short' })
  if (day(date) === day(yesterday)) return 'Yesterday'
  if (date.getFullYear() === now.getFullYear()) {
    return formatApiTimestamp(value, { month: 'short', day: 'numeric' })
  }
  return formatApiTimestamp(value, { dateStyle: 'medium' })
}
