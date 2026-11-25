import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { checkoutBook, listActiveLoans, listOverdueLoans, returnLoan } from '../../api/loans'
import { queryKeys } from '../../api/queryKeys'
import type { CheckoutRequest } from '../../api/types'

/** Active loans of one member (at most 3 by the loan limit). Disabled until a member is chosen. */
export function useActiveLoans(memberId: number | undefined) {
  return useQuery({
    queryKey: queryKeys.activeLoans({ memberId }),
    queryFn: ({ signal }) => listActiveLoans({ memberId }, signal),
    enabled: memberId !== undefined,
  })
}

/** Every overdue loan, most overdue first, exactly as the server ordered them (AC-4.BE.9). */
export function useOverdueLoans() {
  return useQuery({
    queryKey: queryKeys.overdueLoans,
    queryFn: ({ signal }) => listOverdueLoans(signal),
    placeholderData: keepPreviousData,
  })
}

/**
 * A loan changes loan lists, copy counts (books) and loan counts (members), so all four cache
 * families are refreshed (architecture 9.3). Returns the promise so callers can wait for fresh data.
 */
function refreshAfterLoanChange(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.activeLoansRoot }),
    queryClient.invalidateQueries({ queryKey: queryKeys.overdueLoans }),
    queryClient.invalidateQueries({ queryKey: queryKeys.booksRoot }),
    queryClient.invalidateQueries({ queryKey: queryKeys.membersRoot }),
  ])
}

/**
 * Checkout. A rejection also refreshes the pickers: the reason is often stale data (a copy was
 * taken, a member was deactivated), and the refreshed lists show the current state.
 */
export function useCheckout() {
  const queryClient = useQueryClient()
  const refresh = () => {
    void refreshAfterLoanChange(queryClient)
  }
  return useMutation({
    mutationFn: (request: CheckoutRequest) => checkoutBook(request),
    onSuccess: refresh,
    onError: refresh,
  })
}

/**
 * Return waits for the refreshed lists before the mutation counts as finished, so the confirmation
 * dialog closes only once the loan row is gone and focus can move to the notice (DR-11). A failed
 * return (409 `LOAN_ALREADY_RETURNED`, 404) also refreshes so a stale row is corrected.
 */
export function useReturnLoan() {
  const queryClient = useQueryClient()
  const refresh = () => refreshAfterLoanChange(queryClient)
  return useMutation({
    mutationFn: (id: number) => returnLoan(id),
    onSuccess: refresh,
    onError: refresh,
  })
}
