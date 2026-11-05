import { describe, expect, it } from 'vitest'
import css from './index.css?raw'
import { motion, palette, radius, theme } from './theme'

function kebab(name: string) {
  return name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)
}

describe('palette', () => {
  it.each(Object.entries(palette))('declares %s with the same value in index.css', (name, hex) => {
    const match = new RegExp(`--color-${kebab(name)}:\\s*(#[0-9a-fA-F]{6});`).exec(css)
    expect(match?.[1].toLowerCase()).toBe(hex.toLowerCase())
  })

  it('gives Ant Design and Tailwind the same corner radius', () => {
    expect(css).toContain(`--radius-ui: ${radius}px;`)
  })

  it('gives Tailwind and Ant Design the same motion (ADR 061)', () => {
    expect(css).toContain(`--default-transition-duration: ${motion.ms}ms;`)
    expect(css).toContain(`--default-transition-timing-function: ${motion.easing};`)
    expect(theme.token).toMatchObject({
      motionDurationMid: '0.15s',
      motionEaseOut: motion.easing,
    })
  })

  it('marks a selected option with the stone ground, not a grey from the ink primary', () => {
    expect(theme.token?.controlItemBgActive).toBe(palette.ground)
    expect(theme.token?.controlItemBgActiveHover).toBe(palette.ground)
  })

  it('shows placeholders in the muted text colour, and the drop zone as a surface', () => {
    expect(theme.token?.colorTextPlaceholder).toBe(palette.muted)
    expect(theme.components?.Upload).toMatchObject({
      colorFillAlter: palette.surface,
      colorBorder: palette.field,
    })
  })

  it('declares no color in index.css that theme.ts lacks', () => {
    const declared = [...css.matchAll(/--color-([a-z-]+):/g)].map((m) => m[1])
    expect(declared.sort()).toEqual(Object.keys(palette).map(kebab).sort())
  })
})
