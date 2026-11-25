import { http, HttpResponse, type RequestHandler } from 'msw'
import type { BookResponse, CheckoutRequest, FieldErrorDto, LoanResponse, MemberResponse } from '../../api/types'
import { apiErrorResponse, pageOf, readPageQuery, recordedUrl, type RecordedRequest } from './common'

/** The fake server's "today"; checkout dates and overdue status are computed from it. */
export const FAKE_TODAY = '2026-11-25'

const DAY_MS = 24 * 60 * 60 * 1000
const MAX_ACTIVE_LOANS = 3
const LOAN_DAYS = 14

function toEpochDay(date: string): number {
  return Date.parse(`${date}T00:00:00Z`) / DAY_MS
}

/** `yyyy-MM-dd` plus `days` (negative allowed), calendar arithmetic without time-zone effects. */
export function addDays(date: string, days: number): string {
  return new Date((toEpochDay(date) + days) * DAY_MS).toISOString().slice(0, 10)
}

/** A loan as the backend returns it (checkout, return, get-by-id and every list item). */
export function makeLoan(overrides: Partial<LoanResponse> = {}): LoanResponse {
  return {
    id: 1,
    member: { id: 1, name: 'Ada Lovelace' },
    book: { id: 1, title: 'Clean Code', isbn: '9780132350884' },
    checkoutDate: addDays(FAKE_TODAY, -3),
    dueDate: addDays(FAKE_TODAY, 11),
    returnDate: null,
    overdue: false,
    daysOverdue: 0,
    ...overrides,
  }
}

/** An unreturned loan whose due date is `daysOverdue` days before the fake today. */
export function makeOverdueLoan(daysOverdue: number, overrides: Partial<LoanResponse> = {}): LoanResponse {
  const dueDate = addDays(FAKE_TODAY, -daysOverdue)
  return makeLoan({
    checkoutDate: addDays(dueDate, -LOAN_DAYS),
    dueDate,
    overdue: daysOverdue > 0,
    daysOverdue,
    ...overrides,
  })
}

export interface FakeLoanApiOptions {
  loans?: LoanResponse[]
  /** Live arrays of the book/member fakes; checkout and return keep their counters consistent. */
  books?: BookResponse[]
  members?: MemberResponse[]
}

export interface FakeLoanApi {
  loans: LoanResponse[]
  requests: RecordedRequest[]
  handlers: RequestHandler[]
}

/**
 * In-memory stand-in for `/api/loans` with the real contract: checkout 201 (due = today + 14) or
 * 404/409 with the codes MEMBER_NOT_FOUND, BOOK_NOT_FOUND, MEMBER_INACTIVE, DUPLICATE_ACTIVE_LOAN,
 * LOAN_LIMIT_REACHED, NO_COPY_AVAILABLE; return 200 or 404 LOAN_NOT_FOUND / 409 LOAN_ALREADY_RETURNED;
 * `/active` is a page ordered by due date then id; `/overdue` is a plain array, most overdue first.
 * Register with `server.use(...api.handlers)`.
 */
export function createFakeLoanApi({ loans: initial = [], books = [], members = [] }: FakeLoanApiOptions = {}): FakeLoanApi {
  const loans = initial.map((loan) => ({ ...loan }))
  const requests: RecordedRequest[] = []
  let nextId = loans.reduce((max, loan) => Math.max(max, loan.id), 0) + 1

  const isActive = (loan: LoanResponse) => loan.returnDate === null
  const byDueDate = (a: LoanResponse, b: LoanResponse) => a.dueDate.localeCompare(b.dueDate) || a.id - b.id
  const loanNotFound = () => apiErrorResponse(404, 'LOAN_NOT_FOUND', 'No loan with this id exists.')
  const conflict = (code: string, message: string) => apiErrorResponse(409, code, message)

  const handlers: RequestHandler[] = [
    http.post('/api/loans', async ({ request }) => {
      const body = (await request.json()) as Partial<CheckoutRequest>
      requests.push({ method: 'POST', url: recordedUrl(request), body })
      const fieldErrors: FieldErrorDto[] = []
      if (typeof body.memberId !== 'number') fieldErrors.push({ field: 'memberId', message: 'must not be null' })
      if (typeof body.bookId !== 'number') fieldErrors.push({ field: 'bookId', message: 'must not be null' })
      if (fieldErrors.length > 0) {
        return apiErrorResponse(400, 'VALIDATION_FAILED', 'Validation failed.', fieldErrors)
      }
      const member = members.find((candidate) => candidate.id === body.memberId)
      if (!member) return apiErrorResponse(404, 'MEMBER_NOT_FOUND', 'No member with this id exists.')
      const book = books.find((candidate) => candidate.id === body.bookId)
      if (!book) return apiErrorResponse(404, 'BOOK_NOT_FOUND', 'No book with this id exists.')
      if (member.status !== 'ACTIVE') return conflict('MEMBER_INACTIVE', 'The member is inactive.')
      const memberLoans = loans.filter((loan) => isActive(loan) && loan.member.id === member.id)
      if (memberLoans.some((loan) => loan.book.id === book.id)) {
        return conflict('DUPLICATE_ACTIVE_LOAN', 'The member already has this book on loan.')
      }
      if (memberLoans.length >= MAX_ACTIVE_LOANS) {
        return conflict('LOAN_LIMIT_REACHED', 'The member already has 3 active loans.')
      }
      if (book.availableCopies < 1) return conflict('NO_COPY_AVAILABLE', 'No copy of this book is available.')

      const loan: LoanResponse = {
        id: nextId++,
        member: { id: member.id, name: member.name },
        book: { id: book.id, title: book.title, isbn: book.isbn },
        checkoutDate: FAKE_TODAY,
        dueDate: addDays(FAKE_TODAY, LOAN_DAYS),
        returnDate: null,
        overdue: false,
        daysOverdue: 0,
      }
      loans.push(loan)
      book.availableCopies -= 1
      member.activeLoanCount += 1
      return HttpResponse.json(loan, { status: 201, headers: { Location: `/api/loans/${loan.id}` } })
    }),

    http.get('/api/loans/active', ({ request }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const url = new URL(request.url)
      const memberId = url.searchParams.get('memberId')
      const bookId = url.searchParams.get('bookId')
      const matches = loans
        .filter(isActive)
        .filter((loan) => !memberId || String(loan.member.id) === memberId)
        .filter((loan) => !bookId || String(loan.book.id) === bookId)
        .sort(byDueDate)
      const { page, size } = readPageQuery(url)
      return HttpResponse.json(pageOf(matches, page, size))
    }),

    http.get('/api/loans/overdue', ({ request }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      return HttpResponse.json(loans.filter((loan) => isActive(loan) && loan.overdue).sort(byDueDate))
    }),

    http.get('/api/loans/:id', ({ request, params }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const loan = loans.find((candidate) => String(candidate.id) === String(params.id))
      return loan ? HttpResponse.json(loan) : loanNotFound()
    }),

    http.post('/api/loans/:id/return', ({ request, params }) => {
      requests.push({ method: 'POST', url: recordedUrl(request) })
      const loan = loans.find((candidate) => String(candidate.id) === String(params.id))
      if (!loan) return loanNotFound()
      if (!isActive(loan)) return conflict('LOAN_ALREADY_RETURNED', 'The loan has already been returned.')
      loan.returnDate = FAKE_TODAY
      loan.overdue = false
      loan.daysOverdue = 0
      const book = books.find((candidate) => candidate.id === loan.book.id)
      if (book) book.availableCopies += 1
      const member = members.find((candidate) => candidate.id === loan.member.id)
      if (member) member.activeLoanCount -= 1
      return HttpResponse.json(loan)
    }),
  ]

  return { loans, requests, handlers }
}
