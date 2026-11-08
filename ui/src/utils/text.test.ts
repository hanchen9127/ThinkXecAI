import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { countLabel, errorMessage } from './text'

describe('countLabel', () => {
  it.each([
    [0, 'no tools'],
    [1, '1 tool'],
    [2, '2 tools'],
  ])('labels %i', (count, label) => {
    expect(countLabel(count, 'tool', 'tools')).toBe(label)
  })
})

describe('errorMessage', () => {
  it("keeps the backend's message for a request it refused, as a sentence", () => {
    expect(errorMessage(new ApiError(400, 'name: must not be blank'))).toBe(
      'name: must not be blank.',
    )
    expect(errorMessage(new ApiError(409, 'Chat session has no agent.'))).toBe(
      'Chat session has no agent.',
    )
  })

  it('says what a missing item means', () => {
    expect(errorMessage(new ApiError(404, 'Chat session not found'))).toBe(
      'It no longer exists; it may have been deleted in another tab.',
    )
  })

  it("keeps a service's own message and replaces any other server error", () => {
    expect(errorMessage(new ApiError(503, 'A service did not answer in time.'))).toBe(
      'A service did not answer in time.',
    )
    expect(errorMessage(new ApiError(500, 'Internal server error'))).toBe(
      'The server ran into a problem. Try again; if it keeps happening, check the server log.',
    )
  })

  it('says the server could not be reached for anything else', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe(
      "Can't reach the server. Check that it is running, then try again.",
    )
  })
})
