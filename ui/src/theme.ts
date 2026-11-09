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
  execute: '#B4560A',
  executeInk: '#8A4108',
  danger: '#A0331B',
  /** Behind a failure that stays on screen (an upload that was refused). */
  dangerWash: '#FBEDE9',
  rail: '#2A2D31',
  railActive: '#3A3E44',
  railIcon: '#B9BDC3',
} as const

/** Corner radius in px; index.css declares the same as `--radius-ui`. */
export const radius = 4

/**
 * Every transition (ADR 061): 150 ms, ease-out. index.css declares the same as Tailwind's default
 * transition; theme.test.ts keeps the two equal.
 */
export const motion = { ms: 150, easing: 'cubic-bezier(0.2, 0, 0, 1)' } as const

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const theme: ThemeConfig = {
  token: {
    // Primary buttons, links, and focus are ink; indigo is kept for Think steps.
    colorPrimary: palette.ink,
    colorError: palette.danger,
    colorText: palette.ink,
    colorTextSecondary: palette.muted,
    // Muted, not Ant Design's 25% black: that is 1.8:1 on white, and this is what the field asks.
    colorTextPlaceholder: palette.muted,
    colorBorder: palette.line,
    colorBorderSecondary: palette.hairline,
    colorBgLayout: palette.ground,
    colorBgContainer: palette.surface,
    // The selected row of selects and menus, which would derive a grey from the ink primary. It
    // keeps the colour while active too: a select opens with its selected row active.
    controlItemBgActive: palette.ground,
    controlItemBgActiveHover: palette.ground,
    borderRadius: radius,
    fontSize: 14,
    fontFamily: "'Instrument Sans', system-ui, sans-serif",
    fontFamilyCode: "'JetBrains Mono', ui-monospace, monospace",
    // Dialogs and menus move like the rest; with reduced motion they do not move at all.
    motion: !reducedMotion,
    motionDurationFast: `${motion.ms / 1000}s`,
    motionDurationMid: `${motion.ms / 1000}s`,
    motionDurationSlow: `${motion.ms / 1000}s`,
    motionEaseOut: motion.easing,
    motionEaseInOut: motion.easing,
  },
  components: {
    Input: { colorBorder: palette.field },
    Select: { colorBorder: palette.field },
    // The drop zone is a white surface with a border that can be seen, like an input.
    Upload: { colorFillAlter: palette.surface, colorBorder: palette.field },
  },
}

/**
 * Stop beside Send (ADR 060): outlined in ink, which a default button is not (its border is the light
 * line). Hover and press keep it ink; "Stopping…" is the same button, dimmed by the Composer.
 */
export const stopTheme: ThemeConfig = {
  components: {
    Button: {
      defaultBorderColor: palette.ink,
      defaultColor: palette.ink,
      defaultHoverBorderColor: palette.ink,
      defaultHoverColor: palette.ink,
      defaultActiveBorderColor: palette.ink,
      defaultActiveColor: palette.ink,
    },
  },
}

/** A slight press on every button (ADR 061, M6); list rows press with `active:scale-98`. */
export const buttonConfig = { className: 'not-disabled:active:scale-97' }
