import type { ThemeConfig } from 'antd'

// The stone palette (ADR 058): ink for text and actions, the Think and Execute colours only for
// steps. index.css declares the same values for Tailwind (`--color-<name>`); theme.test.ts keeps the
// two equal.
export const palette = {
  ground: '#F3F3F0',
  surface: '#FFFFFF',
  ink: '#1F2226',
  muted: '#5C6066',
  line: '#DEDED9',
  hairline: '#E8E8E4',
  /** Input borders: 3:1 against white, as WCAG asks of a control's boundary. */
  field: '#8D9197',
  think: '#4453C4',
  thinkInk: '#2F3A93',
  thinkWash: '#E3E7FA',
  execute: '#B4560A',
  executeInk: '#8A4108',
  executeWash: '#FBEBDD',
  danger: '#A0331B',
  /** Behind a failure that stays on screen (an upload that was refused). */
  dangerWash: '#FBEDE9',
  rail: '#2A2D31',
  railActive: '#3A3E44',
  railIcon: '#B9BDC3',
} as const

/** Corner radius in px; index.css declares the same as `--radius-ui`. */
export const radius = 4

export const theme: ThemeConfig = {
  token: {
    // Primary buttons, links, and focus are ink; indigo is kept for Think steps.
    colorPrimary: palette.ink,
    colorError: palette.danger,
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
  components: {
    Input: { colorBorder: palette.field },
    Select: { colorBorder: palette.field },
  },
}
