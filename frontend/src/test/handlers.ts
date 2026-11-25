import type { RequestHandler } from 'msw'

/**
 * Default MSW handlers shared by all tests. Intentionally empty: every test registers the
 * endpoints it needs (feature handlers live in `handlers/*.ts`), and any unmocked request
 * fails the test because the server uses `onUnhandledRequest: 'error'`.
 */
export const handlers: RequestHandler[] = []
