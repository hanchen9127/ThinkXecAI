import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Markdown from './Markdown'

function rendered(text: string) {
  return render(<Markdown text={text} />).container
}

describe('Markdown', () => {
  it('renders Markdown', () => {
    const root = rendered('**Nova X12 Lite**\n\n| sku |\n|---|\n| SP-NX12L |')
    expect(root.querySelector('strong')).toHaveTextContent('Nova X12 Lite')
    expect(root.querySelector('td')).toHaveTextContent('SP-NX12L')
  })

  it('shows raw HTML as text and runs nothing', () => {
    const root = rendered('Hi <script>alert(1)</script> <img src="x" onerror="alert(2)"> there')
    expect(root.querySelector('script')).toBeNull()
    expect(root.querySelector('img')).toBeNull()
    expect(root.querySelector('[onerror]')).toBeNull()
    expect(root).toHaveTextContent('<script>alert(1)</script>')
  })

  it('never makes a javascript: link', () => {
    const root = rendered('[click](javascript:alert(1)) and <a href="javascript:alert(2)">raw</a>')
    // The words stay; only a link that would run script is dropped.
    expect(root).toHaveTextContent('click')
    for (const link of Array.from(root.querySelectorAll('a'))) {
      expect(link.getAttribute('href') ?? '').not.toMatch(/^\s*javascript:/i)
    }
  })

  it('opens ordinary links in a new tab without giving it this page', () => {
    const link = rendered('[docs](https://example.com/docs)').querySelector('a')
    expect(link).toHaveAttribute('href', 'https://example.com/docs')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link?.getAttribute('rel')).toContain('noopener')
  })
})
