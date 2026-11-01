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
  it("shows the backend's message", () => {
    expect(errorMessage(new ApiError(400, 'name: must not be blank'))).toBe(
      'name: must not be blank',
    )
  })

  it('says the server could not be reached for anything else', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe("Can't reach the server.")
  })
})
