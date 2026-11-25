// @vitest-environment node
// Importing the Vite config pulls in esbuild, which cannot load under jsdom's TextEncoder.
import { describe, expect, it } from 'vitest'
import config, { buildProxy, DEFAULT_API_PROXY_TARGET } from './vite.config'

describe('vite.config proxy and server settings (D7, DR-7, DR-16)', () => {
  it('defaults the /api proxy target to http://127.0.0.1:8080', () => {
    expect(DEFAULT_API_PROXY_TARGET).toBe('http://127.0.0.1:8080')
    expect(buildProxy({})['/api'].target).toBe('http://127.0.0.1:8080')
  })

  it('keeps changeOrigin false so the backend Host allowlist sees localhost:5173', () => {
    expect(buildProxy({})['/api'].changeOrigin).toBe(false)
  })

  it('allows the target to be overridden with VITE_API_PROXY_TARGET', () => {
    expect(buildProxy({ VITE_API_PROXY_TARGET: 'http://127.0.0.1:9090' })['/api'].target).toBe(
      'http://127.0.0.1:9090',
    )
  })

  it('serves on 5173 with strictPort and the exported proxy shape', () => {
    expect(config.server?.port).toBe(5173)
    expect(config.server?.strictPort).toBe(true)
    expect(config.server?.proxy).toHaveProperty('/api')
  })

  it('does not widen the Vite host check (DR-16)', () => {
    expect(config.server?.allowedHosts).toBeUndefined()
    expect(config.server?.host).toBeUndefined()
  })
})
