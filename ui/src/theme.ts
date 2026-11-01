import type { ThemeConfig } from 'antd'

// The V1 palette (docs/plans/phase13-frontend.md). index.css declares the same values for Tailwind
// (`--color-<name>`); theme.test.ts keeps the two equal.
export const palette = {
  ground: '#EEF2F5',
  surface: '#FFFFFF',
  ink: '#1B2430',
  muted: '#5A6675',
  line: '#DCE2E8',
  hairline: '#E6EAEE',
  think: '#4453C4',
  thinkInk: '#2F3A93',
  thinkWash: '#E3E7FA',
  execute: '#B4560A',
  executeInk: '#8A4108',
  executeWash: '#FBEBDD',
  rail: '#1B2430',
  railActive: '#2C394B',
  railIcon: '#AEB8C6',
} as const

/** Corner radius in px; index.css declares the same as `--radius-ui`. */
export const radius = 4

export const theme: ThemeConfig = {
  token: {
    colorPrimary: palette.think,
    colorText: palette.ink,
    colorTextSecondary: palette.muted,
    colorBorder: palette.line,
    colorBorderSecondary: palette.hairline,
    colorBgLayout: palette.ground,
    colorBgContainer: palette.surface,
    borderRadius: radius,
    fontSize: 14,
    fontFamily: "'Instrument Sans', system-ui, sans-serif",
    fontFamilyCode: "'JetBrains Mono', ui-monospace, monospace",
  },
}
