import { describe, expect, it } from 'vitest'
import css from './index.css?raw'
import { palette } from './theme'

// ADR 058's rules, checked against the source so a later change cannot quietly undo them.
const sources = import.meta.glob<string>(['./**/*.tsx', '!./**/*.test.tsx'], {
  query: '?raw',
  import: 'default',
  eager: true,
})

/** WCAG contrast ratio of two #RRGGBB colours. */
function contrast(a: string, b: string) {
  const luminance = (hex: string) => {
    const [r, g, b] = [1, 3, 5].map((i) => {
      const c = parseInt(hex.slice(i, i + 2), 16) / 255
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    })
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (light + 0.05) / (dark + 0.05)
}

describe('the look (ADR 058)', () => {
  it('uses the Think and Execute colours only for steps, and for the mark before a tool', () => {
    const outside = Object.entries(sources)
      .filter(([path]) => !/\/(?:chat\/AgentRun|Tag)\.tsx$/.test(path))
      .filter(([, source]) =>
        /\b(?:text|bg|border|outline|stroke|fill)-(?:think|execute)/.test(source),
      )
      .map(([path]) => path)
    expect(outside).toEqual([])
  })

  it('styles Ant Design with tokens, not by overriding its classes', () => {
    expect(css).not.toMatch(/\.ant-/)
  })

  it('draws no pill tags and no robot icon', () => {
    for (const [path, source] of Object.entries(sources)) {
      expect(source, path).not.toMatch(/RobotOutlined|bg-(?:think|execute)-wash/)
    }
  })

  it.each([
    ['muted text on the ground', palette.muted, palette.ground, 4.5],
    ['white text on ink buttons', '#FFFFFF', palette.ink, 4.5],
    ['rail icons and labels on the rail', palette.railIcon, palette.rail, 4.5],
    ["the rail's white focus ring", '#FFFFFF', palette.rail, 3],
    ['input borders on white', palette.field, palette.surface, 3],
  ])('gives %s enough contrast', (_, foreground, background, minimum) => {
    expect(contrast(foreground, background)).toBeGreaterThanOrEqual(minimum)
  })
})
