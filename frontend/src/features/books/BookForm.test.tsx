import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { createFakeBookApi, makeBook, sampleBooks } from '../../test/handlers/books'
import { apiErrorResponse, proxyDownResponse } from '../../test/handlers/common'
import { createTestQueryClient, renderApp } from '../../test/render'
import { server } from '../../test/server'

const VALID = {
  title: 'Refactoring',
  author: 'Martin Fowler',
  isbn: '978-0-13-475759-9',
  totalCopies: '2',
}

function field(label: string): HTMLInputElement {
  return screen.getByLabelText(new RegExp(`^${label}`)) as HTMLInputElement
}

async function fill(user: UserEvent, values: Partial<typeof VALID>) {
  for (const [name, label] of [
    ['title', 'Title'],
    ['author', 'Author'],
    ['isbn', 'ISBN'],
    ['totalCopies', 'Total copies'],
  ] as const) {
    const value = values[name]
    if (value === undefined) continue
    await user.clear(field(label))
    if (value !== '') await user.type(field(label), value)
  }
}

function renderCreate() {
  const api = createFakeBookApi()
  server.use(...api.handlers)
  return { api, ...renderApp({ route: '/books/new' }) }
}

const posts = (api: ReturnType<typeof createFakeBookApi>) => api.requests.filter((request) => request.method === 'POST')

describe('AC-1.FE.2 client-side validation', () => {
  it('AC-1.FE.2 shows an inline error next to every empty field and sends nothing to the backend', async () => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    expect(document.title).toBe('Add book - Library')
    await user.click(screen.getByRole('button', { name: 'Add book' }))

    for (const [label, message] of [
      ['Title', 'Title is required.'],
      ['Author', 'Author is required.'],
      ['ISBN', 'ISBN is required.'],
      ['Total copies', 'Total copies is required.'],
    ] as const) {
      expect(field(label)).toBeInvalid()
      expect(field(label)).toHaveAccessibleDescription(expect.stringContaining(message))
    }
    expect(screen.getByRole('alert')).toHaveTextContent('The book was not saved')
    expect(field('Title')).toHaveFocus() // first invalid field
    expect(api.requests).toHaveLength(0)
  })

  it.each([
    ['an ISBN with a wrong check digit', { isbn: '9780132350885' }, 'ISBN', 'ISBN must be a valid ISBN-10 or ISBN-13.'],
    ['an ISBN of the wrong length', { isbn: '12345' }, 'ISBN', 'ISBN must be a valid ISBN-10 or ISBN-13.'],
    ['total copies 0', { totalCopies: '0' }, 'Total copies', 'Total copies must be a whole number between 1 and 999.'],
    ['total copies 1000', { totalCopies: '1000' }, 'Total copies', 'Total copies must be a whole number between 1 and 999.'],
    ['total copies 2.5', { totalCopies: '2.5' }, 'Total copies', 'Total copies must be a whole number between 1 and 999.'],
    ['total copies "abc"', { totalCopies: 'abc' }, 'Total copies', 'Total copies must be a whole number between 1 and 999.'],
    ['a blank (spaces only) title', { title: '   ' }, 'Title', 'Title is required.'],
  ])('AC-1.FE.2 rejects %s inline without calling the backend', async (_name, override, label, message) => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    await fill(user, { ...VALID, ...override })
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(field(label)).toHaveAccessibleDescription(expect.stringContaining(message))
    expect(field(label)).toBeInvalid()
    expect(api.requests).toHaveLength(0)
  })

  it('AC-1.FE.2 rejects a title longer than 255 characters', async () => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    await fill(user, { ...VALID, title: '' })
    await user.click(field('Title'))
    await user.paste('x'.repeat(256))
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(field('Title')).toHaveAccessibleDescription(expect.stringContaining('Title must be at most 255 characters.'))
    expect(api.requests).toHaveLength(0)
  })

  it('AC-1.FE.2 clears an error once the field is corrected and submits', async () => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    await fill(user, { ...VALID, isbn: '9780132350885' })
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(field('ISBN')).toBeInvalid()
    await fill(user, { isbn: VALID.isbn })
    expect(field('ISBN')).toBeValid()
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    await waitFor(() => expect(posts(api)).toHaveLength(1))
  })

  it('AC-1.FE.2 sends trimmed values and the total as a number', async () => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    await fill(user, { title: '  Refactoring ', author: ' Martin Fowler ', isbn: ' 978-0-13-475759-9 ', totalCopies: ' 2 ' })
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    await waitFor(() => expect(posts(api)).toHaveLength(1))
    expect(posts(api)[0].body).toEqual({
      title: 'Refactoring',
      author: 'Martin Fowler',
      isbn: '978-0-13-475759-9',
      totalCopies: 2,
    })
  })

  it('AC-1.FE.2 has no axe violations on the empty form and on the form showing errors', async () => {
    const user = userEvent.setup()
    const { container } = renderCreate()
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    await expectNoA11yViolations(container)
  })
})

describe('AC-1.FE.3 server rejections', () => {
  it('AC-1.FE.3 shows a duplicate ISBN inline on the ISBN field, keeps every entered value and focuses the field', async () => {
    const user = userEvent.setup()
    renderCreate()
    await fill(user, { ...VALID, isbn: '978-0-13-235088-4' }) // same book as "Clean Code", hyphenated
    await user.click(screen.getByRole('button', { name: 'Add book' }))

    await waitFor(() =>
      expect(field('ISBN')).toHaveAccessibleDescription(expect.stringContaining('A book with this ISBN already exists.')),
    )
    expect(field('ISBN')).toBeInvalid()
    expect(field('ISBN')).toHaveFocus()
    expect(field('Title')).toHaveValue(VALID.title)
    expect(field('Author')).toHaveValue(VALID.author)
    expect(field('ISBN')).toHaveValue('978-0-13-235088-4')
    expect(field('Total copies')).toHaveValue(VALID.totalCopies)
    expect(screen.getByRole('button', { name: 'Add book' })).toBeEnabled()
  })

  it('AC-1.FE.3 applies several server field errors, focusing the first in page order', async () => {
    const user = userEvent.setup()
    const { container } = renderCreate()
    server.use(
      http.post('/api/books', () =>
        apiErrorResponse(400, 'VALIDATION_FAILED', 'The request contains invalid fields.', [
          { field: 'isbn', message: 'ISBN must be a valid ISBN-10 or ISBN-13.' },
          { field: 'title', message: 'Title must not be blank.' },
        ]),
      ),
    )
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    await waitFor(() => expect(field('Title')).toBeInvalid())
    expect(field('Title')).toHaveAccessibleDescription(expect.stringContaining('Title must not be blank.'))
    expect(field('ISBN')).toHaveAccessibleDescription(expect.stringContaining('ISBN must be a valid ISBN-10 or ISBN-13.'))
    expect(field('Title')).toHaveFocus()
    expect(field('Author')).toHaveValue(VALID.author)
    await expectNoA11yViolations(container)
  })

  it('AC-1.FE.3 shows a field-less rejection as a form-level message and keeps the values', async () => {
    const user = userEvent.setup()
    renderCreate()
    server.use(http.post('/api/books', () => apiErrorResponse(500, 'INTERNAL_ERROR', 'Unexpected.')))
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The server ran into an unexpected problem. Please try again.')
    expect(field('Title')).toHaveValue(VALID.title)
    expect(field('ISBN')).toHaveValue(VALID.isbn)
    expect(field('Title')).toBeValid()
  })

  it('AC-1.FE.3 AC-X.3 shows a friendly form-level message when the server is unreachable and lets the user resubmit', async () => {
    const user = userEvent.setup()
    const { api } = renderCreate()
    server.use(http.post('/api/books', () => proxyDownResponse(), { once: true }))
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The library server cannot be reached.')
    expect(field('Title')).toHaveValue(VALID.title)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    await waitFor(() => expect(posts(api)).toHaveLength(1))
  })

  it('AC-1.FE.3 shows a server field error for a field this form does not have as a form-level message', async () => {
    const user = userEvent.setup()
    renderCreate()
    server.use(
      http.post('/api/books', () =>
        apiErrorResponse(400, 'VALIDATION_FAILED', 'Bad.', [{ field: 'unknownField', message: 'Nope.' }]),
      ),
    )
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Add book' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Some of the entered values are not valid.')
  })
})

describe('AC-1.FE.6 successful create and edit', () => {
  it('AC-1.FE.6 create navigates to the list, which shows the new book, with a success notice', async () => {
    const user = userEvent.setup()
    const { router, api } = renderCreate()
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Add book' }))

    expect(await screen.findByRole('table', { name: 'Book catalogue' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/books')
    expect(screen.getByText(/"Refactoring" was added to the catalogue\./)).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: 'Refactoring' })).toBeInTheDocument()
    expect(api.books).toHaveLength(4)
    expect(screen.getByRole('heading', { level: 1, name: 'Books' })).toHaveFocus()
  })

  it('AC-1.FE.6 submitting twice quickly sends one request (the button is disabled while sending)', async () => {
    const user = userEvent.setup()
    renderCreate()
    let calls = 0
    server.use(
      http.post('/api/books', async () => {
        calls += 1
        await delay(50)
        return HttpResponse.json({ ...VALID, id: 99, totalCopies: 2, availableCopies: 2 }, { status: 201 })
      }),
    )
    await fill(user, VALID)
    await user.dblClick(screen.getByRole('button', { name: 'Add book' }))
    await screen.findByRole('table', { name: 'Book catalogue' })
    expect(calls).toBe(1)
  })

  it('AC-1.FE.6 edit loads the book, saves changes with PUT and shows a success notice on the list', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi()
    server.use(...api.handlers)
    const { router } = renderApp({ route: '/books/1/edit' })
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(document.title).toBe('Edit book - Library')
    expect(field('Title')).toHaveValue('Clean Code')
    expect(field('Author')).toHaveValue('Robert C. Martin')
    expect(field('ISBN')).toHaveValue('9780132350884')
    expect(field('Total copies')).toHaveValue('3')

    await fill(user, { title: 'Clean Code, 2nd edition', totalCopies: '5' })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('table', { name: 'Book catalogue' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/books')
    expect(screen.getByText(/"Clean Code, 2nd edition" was saved\./)).toBeInTheDocument()
    const row = (await screen.findByRole('cell', { name: 'Clean Code, 2nd edition' })).closest('tr') as HTMLElement
    expect(within(row).getAllByRole('cell').map((cell) => cell.textContent).slice(3, 5)).toEqual(['5', '4'])
    expect(api.requests.filter((request) => request.method === 'PUT')).toHaveLength(1)
  })

  it('AC-1.FE.3 editing the total copies below the copies on loan is reported inline on that field', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi([makeBook({ id: 1, totalCopies: 3, availableCopies: 0 })]) // 3 on loan
    server.use(...api.handlers)
    renderApp({ route: '/books/1/edit' })
    await screen.findByRole('button', { name: 'Save changes' })
    await fill(user, { totalCopies: '2' })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() =>
      expect(field('Total copies')).toHaveAccessibleDescription(expect.stringContaining('cannot be lower than the 3 copies on loan')),
    )
    expect(field('Total copies')).toBeInvalid()
    expect(field('Total copies')).toHaveValue('2')
    expect(field('Total copies')).toHaveFocus()
    expect(field('Title')).toHaveValue('Clean Code')
  })

  it('AC-1.FE.6 the Cancel link returns to the list without saving', async () => {
    const user = userEvent.setup()
    const { router, api } = renderCreate()
    await user.click(screen.getByRole('link', { name: 'Cancel' }))
    expect(router.state.location.pathname).toBe('/books')
    expect(api.requests.filter((request) => request.method !== 'GET')).toHaveLength(0)
  })
})

describe('edit page loading and failure (AC-X.3, DR-19)', () => {
  it('shows a loading indicator, then the form', async () => {
    const api = createFakeBookApi()
    server.use(...api.handlers)
    renderApp({ route: '/books/2/edit' })
    expect(screen.getByText('Loading book…').closest('[role="status"]')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
  })

  it('shows "not found" with a link back and no Retry for a deleted book, and does not retry the request', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi(sampleBooks())
    server.use(...api.handlers)
    const { router, container } = renderApp({
      route: '/books/999/edit',
      queryClient: createTestQueryClient({ appRetryPolicy: true }),
    })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This book no longer exists.')
    expect(within(alert).queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(api.requests.filter((request) => request.url === '/api/books/999')).toHaveLength(1) // 4xx is not retried
    await expectNoA11yViolations(container)
    await user.click(within(alert).getByRole('link', { name: 'Back to Books' }))
    expect(router.state.location.pathname).toBe('/books')
  })

  it('treats a non-numeric id as not found without calling the API', async () => {
    const api = createFakeBookApi()
    server.use(...api.handlers)
    renderApp({ route: '/books/abc/edit' })
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(api.requests).toHaveLength(0)
  })

  it('offers Retry when the server is unreachable and loads the form after it recovers', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi()
    server.use(http.get('/api/books/1', () => proxyDownResponse(), { once: true }), ...api.handlers)
    renderApp({ route: '/books/1/edit' })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The library server cannot be reached.')
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(field('Title')).toHaveValue('Clean Code')
  })

  it('reports a book deleted while the form was open as a form-level message on save', async () => {
    const user = userEvent.setup()
    const api = createFakeBookApi()
    server.use(...api.handlers)
    renderApp({ route: '/books/1/edit' })
    await screen.findByRole('button', { name: 'Save changes' })
    api.books.splice(0, 1)
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This book no longer exists.')
    expect(field('Title')).toHaveValue('Clean Code')
  })
})
