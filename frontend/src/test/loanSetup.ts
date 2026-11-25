import { screen, within } from '@testing-library/react'
import type { RequestHandler } from 'msw'
import type { BookResponse, LoanResponse, MemberResponse } from '../api/types'
import { createFakeBookApi, sampleBooks } from './handlers/books'
import { createFakeLoanApi } from './handlers/loans'
import { createFakeMemberApi, sampleMembers } from './handlers/members'
import { renderApp } from './render'
import { server } from './server'

interface LoanScenario {
  loans?: LoanResponse[]
  books?: BookResponse[]
  members?: MemberResponse[]
  route?: string
  /** Handlers that take precedence over the fakes, registered before the first request is sent. */
  overrides?: RequestHandler[]
}

/** Books, members and loans fakes wired together (checkout/return keep the counters consistent), then the app rendered. */
export function renderLoanScreen({
  loans = [],
  books = sampleBooks(),
  members = sampleMembers(),
  route = '/loans',
  overrides = [],
}: LoanScenario = {}) {
  const bookApi = createFakeBookApi(books)
  const memberApi = createFakeMemberApi(members)
  const loanApi = createFakeLoanApi({ loans, books: bookApi.books, members: memberApi.members })
  server.use(...overrides, ...bookApi.handlers, ...memberApi.handlers, ...loanApi.handlers)
  return { bookApi, memberApi, loanApi, ...renderApp({ route }) }
}

export const issueSection = () => within(screen.getByRole('region', { name: 'Issue a book' }))
export const returnSection = () => within(screen.getByRole('region', { name: 'Return a book' }))
