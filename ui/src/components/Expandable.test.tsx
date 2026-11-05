import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
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

  it('gives links and plain buttons one focus ring that a class can still replace', () => {
    expect(css).toMatch(
      /@layer base \{\s*a:focus-visible,\s*button:focus-visible \{\s*outline: 2px solid var\(--color-ink\)/,
    )
  })

  it('never grows wider than its parent', () => {
    expect(css).toMatch(/\.expandable \{[^}]*grid-template-columns: minmax\(0, 1fr\)/)
  })

  it('shows nothing of its content once closed', () => {
    const rules = [...css.matchAll(/([^{}]+)\{([^{}]*overflow-clip-margin[^{}]*)\}/g)]
    expect(rules.map((rule) => rule[1].replace(/\/\*[^]*\*\//, '').trim())).toEqual([
      '.expandable[data-open] > div',
    ])
  })
})
