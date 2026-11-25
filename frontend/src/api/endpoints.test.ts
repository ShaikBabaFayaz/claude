import { http as mswHttp, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { createBook, deleteBook, getBook, listBooks, updateBook } from './books'
import { checkoutBook, getLoan, listActiveLoans, listOverdueLoans, returnLoan } from './loans'
import {
  createMember,
  deactivateMember,
  getMember,
  listMembers,
  reactivateMember,
  updateMember,
} from './members'
import { queryKeys } from './queryKeys'

/** Records `METHOD path?query` of every API call and answers with an empty JSON object. */
function recordCalls() {
  const calls: string[] = []
  server.use(
    mswHttp.all('/api/*', ({ request }) => {
      const url = new URL(request.url)
      calls.push(`${request.method} ${url.pathname}${url.search}`)
      return HttpResponse.json({})
    }),
  )
  return calls
}

describe('typed endpoint functions use the routes of architecture 6.3', () => {
  it('books', async () => {
    const calls = recordCalls()
    await listBooks({ title: 'clean', availability: 'available', page: 2, size: 20 })
    await getBook(7)
    await createBook({ title: 'T', author: 'A', isbn: '9780306406157', totalCopies: 2 })
    await updateBook(7, { title: 'T', author: 'A', isbn: '9780306406157', totalCopies: 3 })
    await deleteBook(7)
    expect(calls).toEqual([
      'GET /api/books?title=clean&availability=available&page=2&size=20',
      'GET /api/books/7',
      'POST /api/books',
      'PUT /api/books/7',
      'DELETE /api/books/7',
    ])
  })

  it('members', async () => {
    const calls = recordCalls()
    await listMembers({ status: 'ACTIVE', search: 'ada', page: 0 })
    await getMember(3)
    await createMember({ name: 'Ada', email: 'ada@example.org' })
    await updateMember(3, { name: 'Ada', email: 'ada@example.org' })
    await deactivateMember(3)
    await reactivateMember(3)
    expect(calls).toEqual([
      'GET /api/members?status=ACTIVE&search=ada&page=0',
      'GET /api/members/3',
      'POST /api/members',
      'PUT /api/members/3',
      'POST /api/members/3/deactivate',
      'POST /api/members/3/reactivate',
    ])
  })

  it('loans', async () => {
    const calls = recordCalls()
    await checkoutBook({ memberId: 1, bookId: 2 })
    await returnLoan(10)
    await getLoan(10)
    await listActiveLoans({ memberId: 1, page: 0, size: 50 })
    await listOverdueLoans()
    expect(calls).toEqual([
      'POST /api/loans',
      'POST /api/loans/10/return',
      'GET /api/loans/10',
      'GET /api/loans/active?memberId=1&page=0&size=50',
      'GET /api/loans/overdue',
    ])
  })

  it('omits empty filter values from list requests', async () => {
    const calls = recordCalls()
    await listBooks({ title: '', author: undefined })
    await listMembers({})
    await listActiveLoans()
    expect(calls).toEqual(['GET /api/books', 'GET /api/members', 'GET /api/loans/active'])
  })

  it('sends the checkout body as JSON', async () => {
    let body: unknown
    server.use(
      mswHttp.post('/api/loans', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({})
      }),
    )
    await checkoutBook({ memberId: 4, bookId: 9 })
    expect(body).toEqual({ memberId: 4, bookId: 9 })
  })
})

describe('queryKeys (architecture 9.1, 9.3)', () => {
  it('nests list and detail keys under the root key so one invalidation refreshes them all', () => {
    expect(queryKeys.books({ title: 'a' }).slice(0, 1)).toEqual(queryKeys.booksRoot)
    expect(queryKeys.book(1).slice(0, 1)).toEqual(queryKeys.booksRoot)
    expect(queryKeys.members().slice(0, 1)).toEqual(queryKeys.membersRoot)
    expect(queryKeys.member(1).slice(0, 1)).toEqual(queryKeys.membersRoot)
    expect(queryKeys.activeLoans({ memberId: 1 }).slice(0, 1)).toEqual(queryKeys.activeLoansRoot)
  })

  it('differs when the filters differ', () => {
    expect(queryKeys.books({ page: 0 })).not.toEqual(queryKeys.books({ page: 1 }))
    expect(queryKeys.book(1)).not.toEqual(queryKeys.book(2))
  })
})
