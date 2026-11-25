import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { createFakeBookApi, makeBook } from '../../test/handlers/books'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const SCRIPT_TITLE = '<script>alert(1)</script>'
const IMG_AUTHOR = '<img src=x onerror=alert(2)>'

describe('NFR-6 book data is rendered as text, never as HTML', () => {
  it('NFR-6 shows a <script> title and an <img onerror> author literally in the list', async () => {
    const alertSpy = vi.fn()
    vi.stubGlobal('alert', alertSpy)
    try {
      const api = createFakeBookApi([makeBook({ id: 1, title: SCRIPT_TITLE, author: IMG_AUTHOR })])
      server.use(...api.handlers)
      const { container } = renderApp({ route: '/books' })

      const cell = await screen.findByRole('cell', { name: SCRIPT_TITLE })
      expect(cell.textContent).toBe(SCRIPT_TITLE)
      expect(screen.getByRole('cell', { name: IMG_AUTHOR })).toBeInTheDocument()
      expect(container.querySelector('script')).toBeNull()
      expect(container.querySelector('img')).toBeNull()
      expect(alertSpy).not.toHaveBeenCalled()
      expect(within(cell.closest('tr') as HTMLElement).getByRole('button', { name: `Delete ${SCRIPT_TITLE}` })).toBeInTheDocument()
      await expectNoA11yViolations(container)
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('NFR-6 shows the title literally in the delete dialog and in the success notice', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi([makeBook({ id: 1, title: SCRIPT_TITLE, author: IMG_AUTHOR, totalCopies: 1, availableCopies: 1 })])
    server.use(...api.handlers)
    const { container } = renderApp({ route: '/books' })

    await user.click(await screen.findByRole('button', { name: `Delete ${SCRIPT_TITLE}` }))
    const dialog = screen.getByRole('dialog', { name: `Delete "${SCRIPT_TITLE}"?` })
    expect(dialog.querySelector('script')).toBeNull()
    await user.click(within(dialog).getByRole('button', { name: 'Delete book' }))

    expect(await screen.findByText(`"${SCRIPT_TITLE}" was deleted.`)).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
  })

  it('NFR-6 shows the title literally on the edit form', async () => {
    const api = createFakeBookApi([makeBook({ id: 1, title: SCRIPT_TITLE, author: IMG_AUTHOR })])
    server.use(...api.handlers)
    const { container } = renderApp({ route: '/books/1/edit' })
    expect(await screen.findByLabelText(/^Title/)).toHaveValue(SCRIPT_TITLE)
    expect(container.querySelector('script')).toBeNull()
  })
})
