import type { ReactNode } from 'react'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { formatDate } from '../../lib/dates'
import { usePageTitle } from '../../lib/usePageTitle'
import { OverdueBadge } from './OverdueBadge'
import { useOverdueLoans } from './useLoanQueries'

/**
 * Overdue loans (AC-4.FE.5, AC-4.FE.6): member, book, due date and days overdue in the order the
 * server returns them (most overdue first; the client never re-sorts or recomputes, NFR-12).
 * The late status is text plus badge shape and colour.
 */
export function OverduePage() {
  usePageTitle('Overdue loans')
  const query = useOverdueLoans()

  let content: ReactNode
  if (query.isError) {
    content = <ErrorState error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />
  } else if (!query.data) {
    content = <LoadingState label="Loading overdue loans…" />
  } else if (query.data.length === 0) {
    content = <EmptyState title="No overdue loans">Every borrowed book is back or still within its loan period.</EmptyState>
  } else {
    content = (
      <table className="table">
        <caption>Overdue loans, most overdue first</caption>
        <thead>
          <tr>
            <th scope="col">Member</th>
            <th scope="col">Book</th>
            <th scope="col">Due date</th>
            <th scope="col">Days overdue</th>
          </tr>
        </thead>
        <tbody>
          {query.data.map((loan) => (
            <tr key={loan.id}>
              <td>{loan.member.name}</td>
              <td>{loan.book.title}</td>
              <td>{formatDate(loan.dueDate)}</td>
              <td>
                <OverdueBadge daysOverdue={loan.daysOverdue} wording="count" />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    )
  }

  return (
    <>
      <h1 tabIndex={-1}>Overdue loans</h1>
      {content}
    </>
  )
}
