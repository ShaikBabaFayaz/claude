import { builtinEnvironments, type Environment } from 'vitest/environments'

/**
 * Vitest's built-in jsdom environment, except that `AbortController` and `AbortSignal` stay the
 * Node.js ones. jsdom's own classes are rejected by Node's `fetch`/`Request` ("Expected signal to be
 * an instance of AbortSignal"), which would break every router navigation and any request that
 * passes TanStack Query's abort signal. Everything else (DOM, events, storage) is plain jsdom.
 */
const environment: Environment = {
  ...builtinEnvironments.jsdom,
  name: 'jsdom-node-abort',
  async setup(global, options) {
    const { AbortController, AbortSignal } = global as unknown as Pick<
      typeof globalThis,
      'AbortController' | 'AbortSignal'
    >
    const result = await builtinEnvironments.jsdom.setup(global, options)
    Object.assign(global, { AbortController, AbortSignal })
    return result
  },
}

export default environment
