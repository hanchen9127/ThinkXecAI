import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import LoadError from './LoadError'

describe('LoadError', () => {
  it('says what failed to load and why, and loads it again on Try again', async () => {
    const retry = vi.fn()
    render(<LoadError what="the documents" error={new ApiError(500, 'boom')} onRetry={retry} />)

    expect(screen.getByRole('alert')).toHaveTextContent(
      "Couldn't load the documents. The server ran into a problem.",
    )
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(retry).toHaveBeenCalledOnce()
  })

  it('offers no Try again when the caller cannot load it again', () => {
    render(<LoadError what="agents" error={new ApiError(404, 'gone')} />)

    expect(screen.getByRole('alert')).toHaveTextContent("Couldn't load agents. It no longer exists")
    expect(screen.queryByRole('button')).toBeNull()
  })
})
