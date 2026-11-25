import type { LoanResponse } from '../../api/types'
import { formatDate } from '../../lib/dates'
import styles from './CheckoutResult.module.css'

/** Confirmation of a successful checkout: member, book and due date from the server response (AC-4.FE.2). */
export function CheckoutResult({ loan }: { loan: LoanResponse }) {
  return (
    <section aria-labelledby="checkout-result-title" className={styles.result}>
      <h3 id="checkout-result-title">Book checked out</h3>
      <dl className={styles.details}>
        <dt>Member</dt>
        <dd>{loan.member.name}</dd>
        <dt>Book</dt>
        <dd>{loan.book.title}</dd>
        <dt>Due date</dt>
        <dd>{formatDate(loan.dueDate)}</dd>
      </dl>
    </section>
  )
}
