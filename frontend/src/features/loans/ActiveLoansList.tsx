import { useState, type ReactNode } from 'react'
import type { LoanResponse, MemberResponse } from '../../api/types'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { useNotifications } from '../../components/NotificationProvider'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDate } from '../../lib/dates'
import { getErrorMessage } from '../../lib/errorMessages'
import { OverdueBadge } from './OverdueBadge'
import { useActiveLoans, useReturnLoan } from './useLoanQueries'

/**
 * Active loans of the selected member with a Return action behind a confirmation (AC-4.FE.4,
 * NFR-8). After the return the loan row is gone, so focus moves to the notice (DR-11). Overdue
 * loans are flagged by text and badge from the server's `overdue`/`daysOverdue` (NFR-12).
 */
export function ActiveLoansList({ member }: { member: MemberResponse }) {
  const query = useActiveLoans(member.id)
  const returnLoan = useReturnLoan()
  const { notify } = useNotifications()
  const [target, setTarget] = useState<LoanResponse | null>(null)

  function confirmReturn() {
    if (!target) return
    const loan = target
    returnLoan.mutate(loan.id, {
      onSuccess: () => {
        setTarget(null)
        notify(`"${loan.book.title}" was returned by ${loan.member.name}.`, { focus: true })
      },
      onError: (error) => {
        setTarget(null)
        notify(getErrorMessage(error), { kind: 'error', focus: true })
      },
    })
  }

  let content: ReactNode
  if (query.isError) {
    content = <ErrorState error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />
  } else if (!query.data) {
    content = <LoadingState label="Loading loans…" />
  } else if (query.data.content.length === 0) {
    content = <p>{member.name} has no books on loan.</p>
  } else {
    content = (
      <table className="table">
        <caption>Books on loan to {member.name}</caption>
        <thead>
          <tr>
            <th scope="col">Book</th>
            <th scope="col">Checkout date</th>
            <th scope="col">Due date</th>
            <th scope="col">Status</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {query.data.content.map((loan) => (
            <tr key={loan.id}>
              <td>{loan.book.title}</td>
              <td>{formatDate(loan.checkoutDate)}</td>
              <td>{formatDate(loan.dueDate)}</td>
              <td>
                {loan.overdue ? (
                  <OverdueBadge daysOverdue={loan.daysOverdue} />
                ) : (
                  <StatusBadge tone="success">On time</StatusBadge>
                )}
              </td>
              <td>
                <button type="button" className="button button--small" onClick={() => setTarget(loan)}>
                  Return<span className="visually-hidden"> {loan.book.title}</span>
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    )
  }

  return (
    <>
      {content}
      <ConfirmDialog
        open={target !== null}
        title={target ? `Return "${target.book.title}"?` : 'Return book?'}
        confirmLabel="Return book"
        busy={returnLoan.isPending}
        onCancel={() => setTarget(null)}
        onConfirm={confirmReturn}
      >
        {target ? `Record that ${target.member.name} has brought back "${target.book.title}".` : ''}
      </ConfirmDialog>
    </>
  )
}
