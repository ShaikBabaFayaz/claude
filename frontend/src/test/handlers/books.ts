import { http, HttpResponse, type RequestHandler } from 'msw'
import type { BookRequest, BookResponse } from '../../api/types'
import { normalizeIsbn } from '../../lib/validators'
import { apiErrorResponse, pageOf, readPageQuery, recordedUrl, type RecordedRequest } from './common'

/** A book as the backend returns it (architecture 6.1). Valid ISBNs are asserted in `fixtures.test.ts`. */
export function makeBook(overrides: Partial<BookResponse> = {}): BookResponse {
  return {
    id: 1,
    title: 'Clean Code',
    author: 'Robert C. Martin',
    isbn: '9780132350884',
    totalCopies: 3,
    availableCopies: 2,
    ...overrides,
  }
}

/** A small, varied catalogue: one book with free copies, one fully on loan, one with a single copy. */
export function sampleBooks(): BookResponse[] {
  return [
    makeBook({ id: 1, title: 'Clean Code', author: 'Robert C. Martin', isbn: '9780132350884', totalCopies: 3, availableCopies: 2 }),
    makeBook({ id: 2, title: 'Design Patterns', author: 'Erich Gamma', isbn: '9780201633610', totalCopies: 1, availableCopies: 0 }),
    makeBook({ id: 3, title: 'The Pragmatic Programmer', author: 'Andrew Hunt', isbn: '9780135957059', totalCopies: 2, availableCopies: 2 }),
  ]
}

/** `count` generated books `Book 001`, `Book 002`, ... for pagination tests. */
export function manyBooks(count: number): BookResponse[] {
  const books: BookResponse[] = []
  for (let i = 1; i <= count; i++) {
    books.push(
      makeBook({
        id: i,
        title: `Book ${String(i).padStart(3, '0')}`,
        author: `Author ${i}`,
        isbn: `9780000000${String(i).padStart(3, '0')}`,
        totalCopies: 2,
        availableCopies: i % 2 === 0 ? 0 : 2,
      }),
    )
  }
  return books
}

function includesIgnoreCase(haystack: string, needle: string): boolean {
  return haystack.toLowerCase().includes(needle.toLowerCase())
}

export interface FakeBookApi {
  /** Live catalogue; tests may read it to see what the fake "server" holds. */
  books: BookResponse[]
  /** Every request the fake handled, in order. */
  requests: RecordedRequest[]
  handlers: RequestHandler[]
}

/**
 * In-memory stand-in for `/api/books` with the contract semantics of architecture 6.3: filters
 * AND-combined (title/author/ISBN contains, case-insensitive; ISBN normalised), availability
 * filter, order `lower(title)` then ISBN, 20 per page, duplicate normalised ISBN -> 409 with a
 * field error, delete with copies on loan -> 409 `BOOK_HAS_ACTIVE_LOANS`, total below active -> 400.
 * Register with `server.use(...api.handlers)`.
 */
export function createFakeBookApi(initial: BookResponse[] = sampleBooks()): FakeBookApi {
  const books = initial.map((book) => ({ ...book }))
  const requests: RecordedRequest[] = []
  let nextId = books.reduce((max, book) => Math.max(max, book.id), 0) + 1

  const activeLoans = (book: BookResponse) => book.totalCopies - book.availableCopies
  const findBook = (id: string | readonly string[] | undefined) => books.find((book) => String(book.id) === String(id))
  const notFound = () => apiErrorResponse(404, 'BOOK_NOT_FOUND', 'No book with this id exists.')
  const isbnTaken = (isbn: string, exceptId?: number) =>
    books.some((book) => book.id !== exceptId && normalizeIsbn(book.isbn) === normalizeIsbn(isbn))
  const duplicateIsbn = () =>
    apiErrorResponse(409, 'ISBN_ALREADY_EXISTS', 'A book with this ISBN already exists.', [
      { field: 'isbn', message: 'A book with this ISBN already exists.' },
    ])

  const handlers: RequestHandler[] = [
    http.get('/api/books', ({ request }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const params = new URL(request.url).searchParams
      const title = params.get('title')
      const author = params.get('author')
      const isbn = params.get('isbn')
      const availability = params.get('availability')
      const matches = books
        .filter((book) => !title || includesIgnoreCase(book.title, title))
        .filter((book) => !author || includesIgnoreCase(book.author, author))
        .filter((book) => !isbn || normalizeIsbn(book.isbn).toLowerCase().includes(normalizeIsbn(isbn).toLowerCase()))
        .filter((book) => availability !== 'available' || book.availableCopies >= 1)
        .filter((book) => availability !== 'unavailable' || book.availableCopies === 0)
        .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()) || a.isbn.localeCompare(b.isbn))
      const { page, size } = readPageQuery(new URL(request.url))
      return HttpResponse.json(pageOf(matches, page, size))
    }),

    http.get('/api/books/:id', ({ request, params }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const book = findBook(params.id)
      return book ? HttpResponse.json(book) : notFound()
    }),

    http.post('/api/books', async ({ request }) => {
      const body = (await request.json()) as BookRequest
      requests.push({ method: 'POST', url: recordedUrl(request), body })
      if (isbnTaken(body.isbn)) return duplicateIsbn()
      const book: BookResponse = {
        id: nextId++,
        title: body.title,
        author: body.author,
        isbn: normalizeIsbn(body.isbn),
        totalCopies: body.totalCopies,
        availableCopies: body.totalCopies,
      }
      books.push(book)
      return HttpResponse.json(book, { status: 201, headers: { Location: `/api/books/${book.id}` } })
    }),

    http.put('/api/books/:id', async ({ request, params }) => {
      const body = (await request.json()) as BookRequest
      requests.push({ method: 'PUT', url: recordedUrl(request), body })
      const book = findBook(params.id)
      if (!book) return notFound()
      if (isbnTaken(body.isbn, book.id)) return duplicateIsbn()
      if (body.totalCopies < activeLoans(book)) {
        return apiErrorResponse(400, 'TOTAL_COPIES_BELOW_ACTIVE_LOANS', 'Total copies cannot be below active loans.', [
          { field: 'totalCopies', message: `Total copies cannot be lower than the ${activeLoans(book)} copies on loan.` },
        ])
      }
      const onLoan = activeLoans(book)
      Object.assign(book, {
        title: body.title,
        author: body.author,
        isbn: normalizeIsbn(body.isbn),
        totalCopies: body.totalCopies,
        availableCopies: body.totalCopies - onLoan,
      })
      return HttpResponse.json(book)
    }),

    http.delete('/api/books/:id', ({ request, params }) => {
      requests.push({ method: 'DELETE', url: recordedUrl(request) })
      const book = findBook(params.id)
      if (!book) return notFound()
      if (activeLoans(book) > 0) {
        return apiErrorResponse(409, 'BOOK_HAS_ACTIVE_LOANS', 'The book has active loans and cannot be deleted.')
      }
      books.splice(books.indexOf(book), 1)
      return new HttpResponse(null, { status: 204 })
    }),
  ]

  return { books, requests, handlers }
}
