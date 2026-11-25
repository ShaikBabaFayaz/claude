import { describe, expect, it } from 'vitest'
import { isValidEmail, isValidIsbn } from '../../lib/validators'
import { sampleBooks } from './books'
import { manyMembers, sampleMembers } from './members'

/**
 * The MSW fixtures stand in for the real backend, so they must at least obey the same rules the
 * backend enforces: otherwise a screen test could pass on data the real API would never return.
 */
describe('MSW fixtures obey the API contract rules', () => {
  it('sample books have valid ISBNs, consistent counters and unique ids', () => {
    const books = sampleBooks()
    for (const book of books) {
      expect(isValidIsbn(book.isbn), book.isbn).toBe(true)
      expect(book.totalCopies).toBeGreaterThanOrEqual(1)
      expect(book.availableCopies).toBeGreaterThanOrEqual(0)
      expect(book.availableCopies).toBeLessThanOrEqual(book.totalCopies)
    }
    expect(new Set(books.map((book) => book.id)).size).toBe(books.length)
  })

  it.each([
    ['sample members', sampleMembers()],
    ['generated members', manyMembers(45)],
  ])('%s have valid, distinct emails and counters within the loan limit', (_name, members) => {
    for (const member of members) {
      expect(isValidEmail(member.email), member.email).toBe(true)
      expect(member.activeLoanCount).toBeGreaterThanOrEqual(0)
      expect(member.activeLoanCount).toBeLessThanOrEqual(3)
      // Invariant of the schema (ck_member_inactive_no_loans): an inactive member has no loans.
      if (member.status === 'INACTIVE') expect(member.activeLoanCount).toBe(0)
    }
    expect(new Set(members.map((member) => member.email.toLowerCase())).size).toBe(members.length)
    expect(new Set(members.map((member) => member.id)).size).toBe(members.length)
  })
})
