import { expect } from 'vitest'
import { configureAxe } from 'vitest-axe'
import * as axeMatchers from 'vitest-axe/matchers'
import type { AxeMatchers } from 'vitest-axe/matchers'

// vitest-axe 0.1.0 types its matchers on the old global `Vi` namespace, which Vitest 3
// no longer reads, so the matcher type is declared against the `vitest` module here.
declare module 'vitest' {
  // The type parameter must match Vitest's own declaration for the interfaces to merge.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/no-empty-object-type, @typescript-eslint/no-unused-vars
  interface Assertion<T = any> extends AxeMatchers {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}

expect.extend(axeMatchers)

// jsdom has no layout engine, so axe cannot evaluate colour contrast here. Contrast is
// covered by `styles/contrast.test.ts` (token ratios) and the manual Stage 7 check (DR-11).
const runAxe = configureAxe({
  rules: { 'color-contrast': { enabled: false } },
})

/** Runs axe on a rendered container and fails the test on any violation (all impacts). */
export async function expectNoA11yViolations(container: Element): Promise<void> {
  const results = await runAxe(container)
  expect(results).toHaveNoViolations()
}
