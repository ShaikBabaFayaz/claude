import { http } from './http'
import type {
  ActiveLoanFilters,
  CheckoutRequest,
  LoanResponse,
  PageResponse,
} from './types'

export function checkoutBook(request: CheckoutRequest) {
  return http.post<LoanResponse>('/loans', { body: request })
}

export function returnLoan(id: number) {
  return http.post<LoanResponse>(`/loans/${id}/return`)
}

export function getLoan(id: number, signal?: AbortSignal) {
  return http.get<LoanResponse>(`/loans/${id}`, { signal })
}

export function listActiveLoans(filters: ActiveLoanFilters = {}, signal?: AbortSignal) {
  return http.get<PageResponse<LoanResponse>>('/loans/active', { query: filters, signal })
}

/** Unpaginated plain JSON array of LoanResponse, most overdue first (AC-4.BE.9). */
export function listOverdueLoans(signal?: AbortSignal) {
  return http.get<LoanResponse[]>('/loans/overdue', { signal })
}
