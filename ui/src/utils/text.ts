import { ApiError } from '../api/http'

/** `no tools`, `1 tool`, `2 tools`. */
export function countLabel(count: number, one: string, many: string): string {
  if (count === 0) return `no ${many}`
  return count === 1 ? `1 ${one}` : `${count} ${many}`
}

const UNREACHABLE = "Can't reach the server. Check that it is running, then try again."

/**
 * Why a request failed and what to do next, in words a person can act on (critique of 2026-11-08);
 * the caller says what failed ("Couldn't send your message."). Mapped once, by status: a 400 or 409
 * keeps the backend's message, which names the field or the conflict, and a 503 keeps its own,
 * written for people (ADR 057); the rest are replaced, as they name code, not a next step. A 502 or 504
 * comes from a proxy put in front of the server when the server is down, so it reads like no answer at
 * all (the Docker setup has no such proxy: the browser calls the backend itself, ADR 062).
 */
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError) || error.code === 502 || error.code === 504) {
    return UNREACHABLE
  }
  if (error.code === 404) return 'It no longer exists; it may have been deleted in another tab.'
  if (error.code === 503) return error.message
  if (error.code >= 500) {
    return 'The server ran into a problem. Try again; if it keeps happening, check the server log.'
  }
  return /[.!?]$/.test(error.message) ? error.message : `${error.message}.`
}
