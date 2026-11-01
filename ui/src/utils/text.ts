import { ApiError } from '../api/http'

/** `no tools`, `1 tool`, `2 tools`. */
export function countLabel(count: number, one: string, many: string): string {
  if (count === 0) return `no ${many}`
  return count === 1 ? `1 ${one}` : `${count} ${many}`
}

/** What to show for a failed request: the backend's own message, or that it could not be reached. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "Can't reach the server."
}
