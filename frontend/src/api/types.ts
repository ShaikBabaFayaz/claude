/**
 * API contract types (architecture 6.1-6.3). Dates are `yyyy-MM-dd` strings; the UI never
 * computes overdue status itself, it uses `overdue` / `daysOverdue` from the server (NFR-12).
 */

/** Error codes produced by the backend (architecture 6.2). */
export const SERVER_ERROR_CODES = [
  'VALIDATION_FAILED',
  'TOTAL_COPIES_BELOW_ACTIVE_LOANS',
  'MALFORMED_REQUEST',
  'MISSING_REQUIRED_HEADER',
  'FORBIDDEN_ORIGIN',
  'BOOK_NOT_FOUND',
  'MEMBER_NOT_FOUND',
  'LOAN_NOT_FOUND',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
  'UNSUPPORTED_MEDIA_TYPE',
  'REQUEST_REJECTED',
  'ISBN_ALREADY_EXISTS',
  'EMAIL_ALREADY_EXISTS',
  'BOOK_HAS_ACTIVE_LOANS',
  'MEMBER_HAS_ACTIVE_LOANS',
  'LOAN_LIMIT_REACHED',
  'NO_COPY_AVAILABLE',
  'MEMBER_INACTIVE',
  'DUPLICATE_ACTIVE_LOAN',
  'LOAN_ALREADY_RETURNED',
  'CONCURRENT_UPDATE',
  'INTERNAL_ERROR',
] as const

/** Client-side only: the server could not be reached or answered with a non-ApiError body (DR-10). */
export const BACKEND_UNAVAILABLE = 'BACKEND_UNAVAILABLE'

export const ERROR_CODES = [...SERVER_ERROR_CODES, BACKEND_UNAVAILABLE] as const

export type ErrorCode = (typeof ERROR_CODES)[number]

export interface FieldErrorDto {
  field: string
  message: string
}

/** The one error body every backend failure uses (AC-X.1). */
export interface ApiErrorBody {
  status: number
  /** Stable machine-readable code. A string, because a newer backend may add codes. */
  code: string
  message: string
  timestamp?: string
  fieldErrors?: FieldErrorDto[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface BookResponse {
  id: number
  title: string
  author: string
  isbn: string
  totalCopies: number
  availableCopies: number
}

export type MemberStatus = 'ACTIVE' | 'INACTIVE'

export interface MemberResponse {
  id: number
  name: string
  email: string
  status: MemberStatus
  activeLoanCount: number
}

export interface LoanResponse {
  id: number
  member: { id: number; name: string }
  book: { id: number; title: string; isbn: string }
  checkoutDate: string
  dueDate: string
  returnDate: string | null
  overdue: boolean
  daysOverdue: number
}

export interface BookRequest {
  title: string
  author: string
  isbn: string
  totalCopies: number
}

export interface MemberRequest {
  name: string
  email: string
}

export interface CheckoutRequest {
  memberId: number
  bookId: number
}

export type BookAvailabilityFilter = 'available' | 'unavailable'

export interface PageParams {
  /** Zero-based page index. */
  page?: number
  size?: number
}

export interface BookFilters extends PageParams {
  title?: string
  author?: string
  isbn?: string
  availability?: BookAvailabilityFilter
}

export interface MemberFilters extends PageParams {
  status?: MemberStatus
  search?: string
}

export interface ActiveLoanFilters extends PageParams {
  memberId?: number
  bookId?: number
}
