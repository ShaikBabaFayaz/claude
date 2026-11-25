/**
 * Colour design tokens (DR-11, NFR-3). This module is the single source of truth: `global.css`
 * repeats the values as CSS custom properties and `contrast.test.ts` fails if the two disagree,
 * and also checks the WCAG contrast of every pair listed in `contrastRequirements`.
 */
export const colorTokens = {
  'color-bg': '#ffffff',
  'color-surface': '#f3f4f6',
  'color-text': '#1f2933',
  'color-text-muted': '#4b5563',
  'color-primary': '#0b4f9c',
  'color-on-primary': '#ffffff',
  'color-border': '#6b7280',
  'color-focus': '#1d4ed8',
  'color-danger': '#b3261e',
  'color-on-danger': '#ffffff',
  'color-danger-bg': '#fdecea',
  'color-success': '#14532d',
  'color-success-bg': '#e7f5ec',
  'color-warning': '#7a3e00',
  'color-warning-bg': '#fff3e0',
  'color-info': '#0b4f9c',
  'color-info-bg': '#e8f0fb',
} as const

export type ColorToken = keyof typeof colorTokens

export interface ContrastRequirement {
  foreground: ColorToken
  background: ColorToken
  /** WCAG 2.1 AA: 4.5 for text (1.4.3), 3 for focus indicators and component boundaries (1.4.11). */
  minimum: 4.5 | 3
  description: string
}

export const contrastRequirements: readonly ContrastRequirement[] = [
  { foreground: 'color-text', background: 'color-bg', minimum: 4.5, description: 'body text' },
  { foreground: 'color-text', background: 'color-surface', minimum: 4.5, description: 'text on panels' },
  { foreground: 'color-text-muted', background: 'color-bg', minimum: 4.5, description: 'secondary text' },
  {
    foreground: 'color-text-muted',
    background: 'color-surface',
    minimum: 4.5,
    description: 'secondary text on panels',
  },
  { foreground: 'color-primary', background: 'color-bg', minimum: 4.5, description: 'links and text buttons' },
  { foreground: 'color-primary', background: 'color-surface', minimum: 4.5, description: 'links on panels' },
  {
    foreground: 'color-on-primary',
    background: 'color-primary',
    minimum: 4.5,
    description: 'primary button label',
  },
  {
    foreground: 'color-on-danger',
    background: 'color-danger',
    minimum: 4.5,
    description: 'destructive button label',
  },
  { foreground: 'color-danger', background: 'color-bg', minimum: 4.5, description: 'inline error text' },
  { foreground: 'color-danger', background: 'color-danger-bg', minimum: 4.5, description: 'error notice text' },
  {
    foreground: 'color-success',
    background: 'color-success-bg',
    minimum: 4.5,
    description: 'success notice and badge text',
  },
  {
    foreground: 'color-warning',
    background: 'color-warning-bg',
    minimum: 4.5,
    description: 'warning and overdue badge text',
  },
  { foreground: 'color-info', background: 'color-info-bg', minimum: 4.5, description: 'info notice text' },
  { foreground: 'color-focus', background: 'color-bg', minimum: 3, description: 'focus indicator on the page' },
  { foreground: 'color-focus', background: 'color-surface', minimum: 3, description: 'focus indicator on panels' },
  { foreground: 'color-border', background: 'color-bg', minimum: 3, description: 'input and table borders' },
  { foreground: 'color-border', background: 'color-surface', minimum: 3, description: 'borders on panels' },
  { foreground: 'color-primary', background: 'color-bg', minimum: 3, description: 'button boundary' },
]

/** CSS text for the tokens, one `  --color-bg: #ffffff;` line each (the shape used in global.css). */
export function toCssCustomProperties(tokens: Record<string, string> = colorTokens): string {
  return Object.entries(tokens)
    .map(([name, value]) => `  --${name}: ${value};`)
    .join('\n')
}
