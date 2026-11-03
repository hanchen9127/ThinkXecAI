import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Expandable from './Expandable'

function view(open: boolean) {
  return (
    <Expandable open={open} motion="slide">
      <button type="button">Inside</button>
    </Expandable>
  )
}

describe('Expandable', () => {
  it('mounts nothing until it first opens', () => {
    const { container } = render(view(false))
    expect(screen.queryByText('Inside')).toBeNull()
    expect(container.firstElementChild).not.toHaveAttribute('data-open')
  })

  it('keeps its content while closing, hidden and out of reach', () => {
    const { container, rerender } = render(view(true))
    const root = container.firstElementChild!
    expect(root).toHaveAttribute('data-open')
    expect(screen.getByRole('button', { name: 'Inside' })).toBeInTheDocument()

    rerender(view(false))
    expect(root).not.toHaveAttribute('data-open')
    expect(root).toHaveAttribute('aria-hidden', 'true')
    expect(root).toHaveAttribute('inert')
    expect(screen.getByText('Inside')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Inside' })).toBeNull()
  })
})
