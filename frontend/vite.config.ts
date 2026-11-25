import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/** Backend address used by the dev-server proxy (architecture D7, DR-16). */
export const DEFAULT_API_PROXY_TARGET = 'http://127.0.0.1:8080'

/**
 * Builds the dev-server proxy table. `changeOrigin: false` keeps the browser's
 * `Host: localhost:5173` so the backend Host/Origin allowlist stays meaningful (DR-7).
 */
export function buildProxy(env: Record<string, string | undefined>) {
  return {
    '/api': {
      target: env.VITE_API_PROXY_TARGET ?? DEFAULT_API_PROXY_TARGET,
      changeOrigin: false,
    },
  }
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // A different port would be rejected by the backend allowlists (R-12), so fail instead.
    strictPort: true,
    proxy: buildProxy(process.env),
  },
  test: {
    // jsdom, but with Node's AbortController/AbortSignal so fetch, MSW and the router accept signals.
    environment: './src/test/jsdomNodeAbort.ts',
    globals: false,
    setupFiles: ['./src/test/setup.ts'],
    // Vitest's default exclude list skips any 'vite.config.*' file, which would hide vite.config.test.ts.
    exclude: ['**/node_modules/**', '**/dist/**'],
  },
})
