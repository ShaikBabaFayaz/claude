import { vi } from 'vitest'

/**
 * Vitest fake timers for tests that also use Testing Library's async helpers or user-event.
 *
 * Testing Library only recognises fake timers when a global `jest` object exists, and without it
 * its `asyncWrapper` waits on a faked `setTimeout` that never fires, so every `user.type` or
 * `findBy*` call hangs. A minimal `jest.advanceTimersByTime` shim makes it advance the Vitest clock
 * instead. Use `useFakeTimersForTesting()` in `beforeEach` and `restoreRealTimers()` in `afterEach`.
 */
export function useFakeTimersForTesting(): void {
  vi.useFakeTimers()
  Object.assign(globalThis, { jest: { advanceTimersByTime: (ms: number) => vi.advanceTimersByTime(ms) } })
}

export function restoreRealTimers(): void {
  vi.useRealTimers()
  delete (globalThis as { jest?: unknown }).jest
}
