import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { restoreRealTimers, useFakeTimersForTesting } from '../../test/fakeTimers'
import { createFakeBookApi, manyBooks, sampleBooks } from '../../test/handlers/books'
import { proxyDownResponse } from '../../test/handlers/common'
import { createTestQueryClient, renderApp } from '../../test/render'
import { server } from '../../test/server'

const titleInput = () => screen.getByLabelText('Title') as HTMLInputElement
const authorInput = () => screen.getByLabelText('Author') as HTMLInputElement
const isbnInput = () => screen.getByLabelText('ISBN') as HTMLInputElement
const availabilitySelect = () => screen.getByLabelText('Availability') as HTMLSelectElement
const count = () => screen.getByText(/\d+ books? found$/)
const listRequests = (api: ReturnType<typeof createFakeBookApi>) =>
  api.requests.filter((request) => request.url === '/api/books' || request.url.startsWith('/api/books?'))

function renderBooks(books = sampleBooks(), route = '/books') {
  const api = createFakeBookApi(books)
  server.use(...api.handlers)
  return { api, ...renderApp({ route }) }
}

function bodyTitles(): string[] {
  const table = screen.getByRole('table')
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[0].textContent ?? '')
}

describe('AC-2.FE.1 filter bar and first page', () => {
  it('AC-2.FE.1 shows filter inputs for title, author, ISBN and availability and the first page of results', async () => {
    renderBooks()
    expect(titleInput()).toBeInTheDocument()
    expect(authorInput()).toBeInTheDocument()
    expect(isbnInput()).toBeInTheDocument()
    expect(Array.from(availabilitySelect().options).map((option) => option.text)).toEqual([
      'All',
      'Available',
      'All copies out',
    ])
    await screen.findByRole('table')
    expect(bodyTitles()).toEqual(['Clean Code', 'Design Patterns', 'The Pragmatic Programmer'])
  })

  it('AC-2.FE.1 restores the filters and the page from the URL (reload / shared link)', async () => {
    const { api } = renderBooks(manyBooks(45), '/books?title=book&author=author&availability=available&page=1')
    await screen.findByRole('table')
    expect(titleInput()).toHaveValue('book')
    expect(authorInput()).toHaveValue('author')
    expect(availabilitySelect()).toHaveValue('available')
    expect(api.requests[0].url).toBe('/api/books?title=book&author=author&availability=available&page=1')
  })

  it('AC-2.FE.1 ignores values in the URL that the API would reject, and cuts text filters to 255 characters (DR-18)', async () => {
    const { api } = renderBooks(sampleBooks(), `/books?availability=bogus&page=-3&title=${'x'.repeat(300)}`)
    await screen.findByRole('heading', { level: 2, name: 'No books found' }) // no book has such a title
    expect(api.requests).toHaveLength(1)
    const url = new URL(api.requests[0].url, 'http://localhost')
    expect(url.searchParams.has('availability')).toBe(false)
    expect(url.searchParams.has('page')).toBe(false)
    expect(url.searchParams.get('title')).toBe('x'.repeat(255))
  })
})

describe('AC-2.FE.2 applying filters', () => {
  it('AC-2.FE.2 applies the filters on submit: the list updates, the URL records them and the match count is announced', async () => {
    const user = userEvent.setup()
    const { api, router } = renderBooks()
    await screen.findByRole('table')
    expect(count()).toHaveTextContent('3 books found')

    await user.type(titleInput(), 'code{Enter}')
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code']))
    expect(router.state.location.search).toBe('?title=code')
    expect(count()).toHaveTextContent('1 book found')
    expect(count().closest('[role="status"]')).toBe(count()) // a live region, so it is announced
    expect(listRequests(api).map((request) => request.url)).toEqual(['/api/books', '/api/books?title=code'])
  })

  it('AC-2.FE.2 combines filters with AND and ignores case; the ISBN filter ignores hyphens', async () => {
    const user = userEvent.setup()
    renderBooks()
    await screen.findByRole('table')
    await user.type(authorInput(), 'ERICH')
    await user.selectOptions(availabilitySelect(), 'unavailable')
    await waitFor(() => expect(bodyTitles()).toEqual(['Design Patterns']))
    await user.clear(authorInput())
    await user.type(authorInput(), 'robert{Enter}') // still "all copies out": nothing matches both
    expect(await screen.findByRole('heading', { level: 2, name: 'No books found' })).toBeInTheDocument()
    await user.clear(authorInput())
    await user.selectOptions(availabilitySelect(), '')
    await user.type(isbnInput(), '978-0-13-235088{Enter}')
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code']))
  })

  it('AC-2.FE.2 availability "Available" lists only books with a free copy', async () => {
    const user = userEvent.setup()
    renderBooks()
    await screen.findByRole('table')
    await user.selectOptions(availabilitySelect(), 'available')
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code', 'The Pragmatic Programmer']))
    expect(count()).toHaveTextContent('2 books found')
  })

  it('AC-2.FE.2 pressing the Search button applies the typed filters', async () => {
    const user = userEvent.setup()
    renderBooks()
    await screen.findByRole('table')
    await user.type(authorInput(), 'hunt')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(bodyTitles()).toEqual(['The Pragmatic Programmer']))
  })

  it('AC-2.FE.2 Back restores the previous filters, in the URL and in the inputs', async () => {
    const user = userEvent.setup()
    const { router } = renderBooks()
    await screen.findByRole('table')
    await user.type(titleInput(), 'code{Enter}')
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code']))
    await user.selectOptions(availabilitySelect(), 'unavailable')
    await screen.findByRole('heading', { level: 2, name: 'No books found' })

    await act(() => router.navigate(-1))
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code']))
    expect(router.state.location.search).toBe('?title=code')
    expect(availabilitySelect()).toHaveValue('')
    expect(titleInput()).toHaveValue('code')

    await act(() => router.navigate(-1))
    await waitFor(() => expect(bodyTitles()).toHaveLength(3))
    expect(titleInput()).toHaveValue('')
    expect(router.state.location.search).toBe('')
  })

  it('AC-2.FE.2 a change of filters goes back to the first page', async () => {
    const user = userEvent.setup()
    const { router } = renderBooks(manyBooks(45), '/books?page=2')
    await screen.findByRole('table')
    await user.selectOptions(availabilitySelect(), 'available')
    await waitFor(() => expect(router.state.location.search).toBe('?availability=available'))
    await waitFor(() => expect(screen.getByText('Page 1 of 2')).toBeInTheDocument())
  })

  it('AC-2.FE.2 shows loading while the first page loads and keeps the previous rows while a new search loads', async () => {
    const user = userEvent.setup()
    renderBooks()
    expect(screen.getByText('Loading books…')).toBeInTheDocument()
    await screen.findByRole('table')
    await user.type(titleInput(), 'pragmatic{Enter}')
    await waitFor(() => expect(bodyTitles()).toEqual(['The Pragmatic Programmer']))
  })
})

describe('AC-2.FE.2 typing pause (fake timers)', () => {
  afterEach(() => restoreRealTimers())

  it('AC-2.FE.2 typing sends one request after the 300 ms pause, not one per keystroke', async () => {
    useFakeTimersForTesting()
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
    const { api } = renderBooks()
    await screen.findByRole('table')
    expect(api.requests).toHaveLength(1)

    await user.type(titleInput(), 'clean')
    act(() => vi.advanceTimersByTime(299))
    expect(api.requests).toHaveLength(1) // still waiting for the pause
    act(() => vi.advanceTimersByTime(1))
    await waitFor(() => expect(api.requests).toHaveLength(2))
    expect(api.requests[1].url).toBe('/api/books?title=clean')
    await screen.findByText('1 book found')

    act(() => vi.advanceTimersByTime(2000))
    expect(api.requests).toHaveLength(2) // no further request from the same typing
  })
})

describe('AC-2.FE.3 availability badges in the list', () => {
  it('AC-2.FE.3 shows "Available (n)" or "All copies out" on every row', async () => {
    renderBooks()
    await screen.findByRole('table')
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[5].textContent)).toEqual([
      expect.stringContaining('Available (2)'),
      expect.stringContaining('All copies out'),
      expect.stringContaining('Available (2)'),
    ])
  })
})

describe('AC-2.FE.4 pagination keeps the filters', () => {
  it('AC-2.FE.4 shows Page x of y and moves between pages through the URL', async () => {
    const user = userEvent.setup()
    const { router, api } = renderBooks(manyBooks(45))
    await screen.findByRole('table')
    expect(bodyTitles()).toHaveLength(20)
    expect(bodyTitles()[0]).toBe('Book 001')
    expect(count()).toHaveTextContent('45 books found')
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(bodyTitles()[0]).toBe('Book 021'))
    expect(router.state.location.search).toBe('?page=1')
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(bodyTitles()).toEqual(['Book 041', 'Book 042', 'Book 043', 'Book 044', 'Book 045']))
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    await waitFor(() => expect(bodyTitles()[0]).toBe('Book 021'))
    expect(api.requests.map((request) => request.url)).toEqual([
      '/api/books',
      '/api/books?page=1',
      '/api/books?page=2',
      '/api/books?page=1',
    ])
  })

  it('AC-2.FE.4 keeps the current filters when changing page', async () => {
    const user = userEvent.setup()
    const { router, api } = renderBooks(manyBooks(45))
    await screen.findByRole('table')
    await user.selectOptions(availabilitySelect(), 'unavailable') // the even-numbered books: 22 of them
    await waitFor(() => expect(count()).toHaveTextContent('22 books found'))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(bodyTitles()).toEqual(['Book 042', 'Book 044']))
    expect(router.state.location.search).toBe('?availability=unavailable&page=1')
    expect(api.requests.at(-1)?.url).toBe('/api/books?availability=unavailable&page=1')
    expect(availabilitySelect()).toHaveValue('unavailable')
  })

  it('AC-2.FE.4 shows no pagination for a single page', async () => {
    renderBooks()
    await screen.findByRole('table')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
  })

  it('AC-2.FE.4 offers a way back when the page in the URL is beyond the last page', async () => {
    const user = userEvent.setup()
    const { router } = renderBooks(manyBooks(25), '/books?page=9')
    expect(await screen.findByRole('heading', { level: 2, name: 'There are no books on this page' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Go to the first page' }))
    await waitFor(() => expect(bodyTitles()).toHaveLength(20))
    expect(router.state.location.search).toBe('')
  })

  it('AC-2.FE.4 has no axe violations on a paginated, filtered page', async () => {
    const { container } = renderBooks(manyBooks(45), '/books?availability=available&page=1')
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })
})

describe('AC-2.FE.5 no matches, loading and error states', () => {
  it('AC-2.FE.5 shows "No books found" with a Clear filters action when filters match nothing', async () => {
    const user = userEvent.setup()
    const { container } = renderBooks(sampleBooks(), '/books?title=zzz')
    expect(await screen.findByRole('heading', { level: 2, name: 'No books found' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No books in the catalogue yet' })).not.toBeInTheDocument()
    expect(count()).toHaveTextContent('0 books found')
    // One "Clear filters" only: the panel's, not the bar's (they would read the same).
    expect(screen.getAllByRole('button', { name: 'Clear filters' })).toHaveLength(1)
    await expectNoA11yViolations(container)

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(bodyTitles()).toHaveLength(3))
    expect(titleInput()).toHaveValue('')
    expect(titleInput()).toHaveFocus() // not lost to <body> when the button disappears
  })

  it('AC-2.FE.5 an empty catalogue is told apart from "no match": no filters means the add-book empty state', async () => {
    renderBooks([])
    expect(await screen.findByRole('heading', { level: 2, name: 'No books in the catalogue yet' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No books found' })).not.toBeInTheDocument()
  })

  it('AC-2.FE.5 clearing filters on a catalogue with no matches does not flash the empty-catalogue state', async () => {
    const user = userEvent.setup()
    renderBooks(sampleBooks(), '/books?title=zzz')
    await screen.findByRole('heading', { level: 2, name: 'No books found' })
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(screen.queryByRole('heading', { name: 'No books in the catalogue yet' })).not.toBeInTheDocument()
    await screen.findByRole('table')
  })

  it('AC-2.FE.5 AC-X.3 shows a friendly error with Retry for a search that fails, and keeps the filters', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi()
    server.use(http.get('/api/books', () => proxyDownResponse(), { once: true }), ...api.handlers)
    const { container } = renderApp({ route: '/books?title=code' })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The library server cannot be reached.')
    expect(titleInput()).toHaveValue('code')
    await expectNoA11yViolations(container)
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(bodyTitles()).toEqual(['Clean Code']))
  })

  it('AC-2.FE.5 retries a failing search once by itself and then shows the error (DR-19)', async () => {
    let calls = 0
    server.use(
      http.get('/api/books', () => {
        calls += 1
        return proxyDownResponse()
      }),
    )
    renderApp({ route: '/books', queryClient: createTestQueryClient({ appRetryPolicy: true }) })
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(calls).toBe(2)
  })
})

describe('AC-2.FE.6 clearing filters', () => {
  it('AC-2.FE.6 "Clear filters" shows the unfiltered first page again and empties the inputs and the URL', async () => {
    const user = userEvent.setup()
    const { router, api } = renderBooks(manyBooks(45), '/books?title=book&availability=available&page=1')
    await screen.findByRole('table')
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    await waitFor(() => expect(count()).toHaveTextContent('45 books found'))
    expect(bodyTitles()[0]).toBe('Book 001')
    expect(titleInput()).toHaveValue('')
    expect(availabilitySelect()).toHaveValue('')
    expect(router.state.location.search).toBe('')
    expect(api.requests.at(-1)?.url).toBe('/api/books')
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument()
    expect(titleInput()).toHaveFocus()
  })

  it('AC-2.FE.6 has no axe violations on the filtered list', async () => {
    const { container } = renderBooks(sampleBooks(), '/books?title=e&availability=available')
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })
})
