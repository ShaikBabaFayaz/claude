import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { createFakeBookApi, sampleBooks } from '../../test/handlers/books'
import { apiErrorResponse, proxyDownResponse } from '../../test/handlers/common'
import { renderApp } from '../../test/render'
import { server } from '../../test/server'

const FRIENDLY_UNAVAILABLE = 'The library server cannot be reached. Check that it is running, then try again.'

function renderBooks(books = sampleBooks(), route = '/books') {
  const api = createFakeBookApi(books)
  server.use(...api.handlers)
  const view = renderApp({ route })
  return { api, ...view }
}

async function rowFor(title: string) {
  const cell = await screen.findByRole('cell', { name: title })
  return cell.closest('tr') as HTMLTableRowElement
}

describe('AC-1.FE.1 book list', () => {
  it('AC-1.FE.1 shows title, author, ISBN, total and available copies with add, edit and delete actions', async () => {
    renderBooks()
    expect(screen.getByRole('heading', { level: 1, name: 'Books' })).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: 'Book catalogue' })
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent)
    expect(headers).toEqual(['Title', 'Author', 'ISBN', 'Total copies', 'Available copies', 'Availability', 'Actions'])

    const row = await rowFor('Clean Code')
    const cells = within(row).getAllByRole('cell').map((cell) => cell.textContent)
    expect(cells.slice(0, 5)).toEqual(['Clean Code', 'Robert C. Martin', '9780132350884', '3', '2'])
    expect(within(row).getByRole('link', { name: 'Edit Clean Code' })).toHaveAttribute('href', '/books/1/edit')
    expect(within(row).getByRole('button', { name: 'Delete Clean Code' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add book' })).toHaveAttribute('href', '/books/new')
    expect(within(table).getAllByRole('row')).toHaveLength(4) // header + 3 books
    expect(within(row).getByText('Available (2)')).toBeInTheDocument()
  })

  it('AC-1.FE.1 sets the page title to "Books - Library"', async () => {
    renderBooks()
    await screen.findByRole('table')
    expect(document.title).toBe('Books - Library')
  })

  it('AC-1.FE.1 the Edit action opens the edit page of that book', async () => {
    const user = userEvent.setup()
    const { router } = renderBooks()
    await user.click(await screen.findByRole('link', { name: 'Edit Design Patterns' }))
    expect(router.state.location.pathname).toBe('/books/2/edit')
    expect(await screen.findByRole('heading', { level: 1, name: 'Edit book' })).toBeInTheDocument()
  })

  it('AC-1.FE.1 has no axe violations on the list', async () => {
    const { container } = renderBooks()
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })
})

describe('AC-1.FE.7 empty, loading and error states', () => {
  it('AC-1.FE.7 shows an empty-state message with an "Add book" action when the catalogue is empty', async () => {
    const user = userEvent.setup()
    const { router, container } = renderBooks([])
    expect(await screen.findByRole('heading', { level: 2, name: 'No books in the catalogue yet' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    // One "Add book" action only: the header button is replaced by the empty-state one.
    const add = screen.getByRole('link', { name: 'Add book' })
    await expectNoA11yViolations(container)
    await user.click(add)
    expect(router.state.location.pathname).toBe('/books/new')
  })

  it('AC-1.FE.7 shows a loading indicator with text while the list is fetched', async () => {
    const api = createFakeBookApi()
    server.use(
      http.get('/api/books', async () => {
        await delay(50)
        return HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }),
      ...api.handlers,
    )
    renderApp({ route: '/books' })
    expect(screen.getByText('Loading books…').closest('[role="status"]')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 2, name: 'No books in the catalogue yet' })).toBeInTheDocument()
  })

  it('AC-1.FE.7 AC-X.3 shows a friendly message with Retry when the server answers 500 with plain text, and Retry recovers', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi()
    server.use(
      http.get('/api/books', () => new HttpResponse('Internal Server Error', { status: 500 }), { once: true }),
      ...api.handlers,
    )
    const { container } = renderApp({ route: '/books' })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(FRIENDLY_UNAVAILABLE)
    expect(alert).not.toHaveTextContent('Internal Server Error')
    await expectNoA11yViolations(container)
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('table', { name: 'Book catalogue' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('AC-1.FE.7 AC-X.3 shows the friendly message for a 502 with an empty body (backend down behind the proxy)', async () => {
    const api = createFakeBookApi()
    server.use(http.get('/api/books', () => proxyDownResponse()), ...api.handlers)
    renderApp({ route: '/books' })
    expect(await screen.findByRole('alert')).toHaveTextContent(FRIENDLY_UNAVAILABLE)
  })

  it('AC-1.FE.7 AC-X.3 shows the friendly message when the network request itself fails', async () => {
    server.use(http.get('/api/books', () => HttpResponse.error()))
    renderApp({ route: '/books' })
    expect(await screen.findByRole('alert')).toHaveTextContent(FRIENDLY_UNAVAILABLE)
  })
})

describe('AC-1.FE.4 delete confirmation', () => {
  it('AC-1.FE.4 opens a labelled confirmation dialog and cancelling leaves the book untouched', async () => {
    const user = userEvent.setup()
    const { api, container } = renderBooks()
    const row = await rowFor('Design Patterns')
    const trigger = within(row).getByRole('button', { name: 'Delete Design Patterns' })
    await user.click(trigger)

    const dialog = screen.getByRole('dialog', { name: 'Delete "Design Patterns"?' })
    expect(dialog).toHaveAccessibleDescription(/cannot be undone/)
    await expectNoA11yViolations(container)

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.requests.filter((request) => request.method === 'DELETE')).toHaveLength(0)
    expect(api.books).toHaveLength(3)
    expect(screen.getByRole('cell', { name: 'Design Patterns' })).toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('AC-1.FE.4 AC-1.FE.6 deletes only after the user confirms, refreshes the list, announces success and moves focus to the notice', async () => {
    const user = userEvent.setup()
    const { api } = renderBooks()
    const row = await rowFor('The Pragmatic Programmer') // 2 of 2 copies free: deletable
    await user.click(within(row).getByRole('button', { name: 'Delete The Pragmatic Programmer' }))
    expect(api.requests.filter((request) => request.method === 'DELETE')).toHaveLength(0)

    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete book' }))

    await waitFor(() => expect(screen.queryByRole('cell', { name: 'The Pragmatic Programmer' })).not.toBeInTheDocument())
    expect(api.requests.filter((request) => request.method === 'DELETE').map((request) => request.url)).toEqual([
      '/api/books/3',
    ])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const notice = (await screen.findByText(/"The Pragmatic Programmer" was deleted\./)).closest('[role="status"]')
    expect(notice).toBeInTheDocument()
    expect(notice).toHaveFocus()
    expect(screen.getByRole('cell', { name: 'Clean Code' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Design Patterns' })).toBeInTheDocument()
  })

  it('AC-1.FE.4 the Esc key (dialog cancel event) cancels without deleting', async () => {
    const user = userEvent.setup()
    const { api } = renderBooks()
    const row = await rowFor('Clean Code')
    await user.click(within(row).getByRole('button', { name: 'Delete Clean Code' }))
    const dialog = screen.getByRole('dialog')
    // jsdom does not turn Escape into a dialog "cancel" event (see ConfirmDialog.test).
    dialog.dispatchEvent(new Event('cancel', { cancelable: true }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(api.requests.filter((request) => request.method === 'DELETE')).toHaveLength(0)
  })

  it('AC-1.FE.5 explains that a book with active loans cannot be deleted and keeps it in the list', async () => {
    const user = userEvent.setup()
    const { api } = renderBooks()
    const row = await rowFor('Clean Code') // 3 copies, 2 available: one is on loan
    await user.click(within(row).getByRole('button', { name: 'Delete Clean Code' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete book' }))

    const notice = await screen.findByText(/cannot be deleted while copies are on loan/)
    expect(notice.closest('[role="status"]')).toHaveFocus()
    expect(notice).toHaveTextContent('Return them first.')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Clean Code' })).toBeInTheDocument()
    expect(api.books).toHaveLength(3)
  })

  it('AC-1.FE.5 a book that was already deleted elsewhere is reported and the stale row disappears', async () => {
    const user = userEvent.setup()
    const { api } = renderBooks()
    const row = await rowFor('The Pragmatic Programmer')
    api.books.splice(2, 1) // someone else deleted it after this list was loaded
    await user.click(within(row).getByRole('button', { name: 'Delete The Pragmatic Programmer' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete book' }))
    expect(await screen.findByText(/no longer exists/)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('cell', { name: 'The Pragmatic Programmer' })).not.toBeInTheDocument())
  })

  it('AC-1.FE.5 AC-X.3 reports an unreachable server while deleting with a friendly message', async () => {
    const user = userEvent.setup()
    const { api } = renderBooks()
    server.use(http.delete('/api/books/:id', () => proxyDownResponse()))
    const row = await rowFor('Design Patterns')
    await user.click(within(row).getByRole('button', { name: 'Delete Design Patterns' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete book' }))
    expect(await screen.findByText(FRIENDLY_UNAVAILABLE)).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Design Patterns' })).toBeInTheDocument()
    expect(api.books).toHaveLength(3)
  })

  it('AC-1.FE.5 shows server error text for a code the client does not know, never raw text', async () => {
    const user = userEvent.setup()
    renderBooks()
    server.use(http.delete('/api/books/:id', () => apiErrorResponse(409, 'SOMETHING_NEW', 'The server says no.')))
    const row = await rowFor('Design Patterns')
    await user.click(within(row).getByRole('button', { name: 'Delete Design Patterns' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete book' }))
    expect(await screen.findByText(/The server says no\./)).toBeInTheDocument()
  })
})
