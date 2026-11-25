import type { ActiveLoanFilters, BookFilters, MemberFilters } from './types'

/**
 * TanStack Query cache keys (architecture 9.1). Each list key extends its root key, so
 * invalidating `queryKeys.booksRoot` refreshes every cached book list and detail (9.3).
 */
export const queryKeys = {
  booksRoot: ['books'] as const,
  books: (filters: BookFilters = {}) => ['books', 'list', filters] as const,
  book: (id: number) => ['books', 'detail', id] as const,

  membersRoot: ['members'] as const,
  members: (filters: MemberFilters = {}) => ['members', 'list', filters] as const,
  member: (id: number) => ['members', 'detail', id] as const,

  activeLoansRoot: ['activeLoans'] as const,
  activeLoans: (filters: ActiveLoanFilters = {}) => ['activeLoans', filters] as const,
  overdueLoans: ['overdueLoans'] as const,
}
