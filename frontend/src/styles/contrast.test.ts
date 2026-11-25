import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { colorTokens, contrastRequirements, toCssCustomProperties, type ColorToken } from './tokens'

/** WCAG 2.1 relative luminance of an sRGB `#rrggbb` colour. */
function luminance(hex: string): number {
  const channels = [1, 3, 5].map((start) => parseInt(hex.slice(start, start + 2), 16) / 255)
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 2.1 contrast ratio, from 1 to 21. */
function contrastRatio(foreground: string, background: string): number {
  const [light, dark] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (light + 0.05) / (dark + 0.05)
}

describe('contrast helper', () => {
  it('matches the WCAG reference values', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#ffffff', '#ffffff')).toBeCloseTo(1, 5)
    expect(contrastRatio('#777777', '#ffffff')).toBeCloseTo(4.48, 1)
  })
})

describe('colour token contrast (NFR-3, DR-11, WCAG 1.4.3 and 1.4.11)', () => {
  it.each(
    contrastRequirements.map(
      (r) => [`${r.description}: ${r.foreground} on ${r.background} (>= ${r.minimum}:1)`, r] as const,
    ),
  )('%s', (_label, requirement) => {
    const ratio = contrastRatio(colorTokens[requirement.foreground], colorTokens[requirement.background])
    expect(ratio).toBeGreaterThanOrEqual(requirement.minimum)
  })

  it('only references tokens that exist and uses valid hex colours', () => {
    for (const value of Object.values(colorTokens)) expect(value).toMatch(/^#[0-9a-f]{6}$/)
    for (const r of contrastRequirements) {
      expect(colorTokens).toHaveProperty(r.foreground)
      expect(colorTokens).toHaveProperty(r.background)
    }
  })

  it('uses every colour token in at least one checked pair, so none escapes the check', () => {
    const checked = new Set<ColorToken>(contrastRequirements.flatMap((r) => [r.foreground, r.background]))
    expect(Object.keys(colorTokens).filter((token) => !checked.has(token as ColorToken))).toEqual([])
  })
})

describe('global.css stays in sync with tokens.ts', () => {
  const css = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), 'global.css'), 'utf8')

  it('declares every token with the same value', () => {
    const declared = Object.fromEntries(
      [...css.matchAll(/--(color-[a-z-]+):\s*(#[0-9a-fA-F]{6});/g)].map((m) => [m[1], m[2].toLowerCase()]),
    )
    expect(declared).toEqual(colorTokens)
  })

  it('contains the generated custom-property block verbatim', () => {
    expect(css).toContain(toCssCustomProperties())
  })

  it('has a visible focus outline of at least 2px and never removes outlines (WCAG 2.4.7)', () => {
    const focusRule = /:focus-visible\s*\{([^}]*)\}/.exec(css)
    expect(focusRule).not.toBeNull()
    const width = /outline:\s*(\d+)px/.exec(focusRule?.[1] ?? '')
    expect(Number(width?.[1])).toBeGreaterThanOrEqual(2)
    expect(css).not.toMatch(/outline\s*:\s*(none|0)\b/)
  })
})
