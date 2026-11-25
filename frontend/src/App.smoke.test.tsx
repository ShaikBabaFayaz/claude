import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { configureAxe } from 'vitest-axe'
import { expectNoA11yViolations } from './test/axe'
import { createFakeBookApi } from './test/handlers/books'
import { renderApp } from './test/render'
import { server } from './test/server'

describe('frontend scaffold smoke test (T-02, updated in T-10 for the real shell)', () => {
  // The default route is the Books screen, which loads the catalogue (T-12).
  beforeEach(() => server.use(...createFakeBookApi().handlers))

  it('renders the application shell inside the shared providers', () => {
    renderApp()
    expect(screen.getByRole('heading', { level: 1, name: 'Books' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('has no axe accessibility violations', async () => {
    const { container } = renderApp()
    await expectNoA11yViolations(container)
  })

  it('axe helper does detect a real violation (image without alt text)', async () => {
    const container = document.createElement('div')
    container.innerHTML = '<img src="data:,">'
    document.body.appendChild(container)
    const results = await configureAxe({ rules: { 'color-contrast': { enabled: false } } })(container)
    expect(results.violations.map((v) => v.id)).toContain('image-alt')
    expect(results).not.toHaveNoViolations()
    container.remove()
  })

  it('serves a mocked API call through MSW (relative /api URL)', async () => {
    server.use(http.get('/api/ping', () => HttpResponse.json({ ok: true })))
    const response = await fetch('/api/ping')
    expect(response.status).toBe(200)
    expect(await response.json()).toEqual({ ok: true })
  })

  it('fails an unmocked request instead of reaching the network (onUnhandledRequest: error)', async () => {
    await expect(fetch('/api/not-mocked')).rejects.toThrow()
  })

  it('polyfills <dialog> showModal/close for jsdom', () => {
    const dialog = document.createElement('dialog')
    document.body.appendChild(dialog)
    dialog.showModal()
    expect(dialog.hasAttribute('open')).toBe(true)
    dialog.close()
    expect(dialog.hasAttribute('open')).toBe(false)
    dialog.remove()
  })
})
