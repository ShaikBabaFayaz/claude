import { useState, type FormEvent } from 'react'
import type { BookResponse, LoanResponse, MemberResponse } from '../../api/types'
import { ErrorState } from '../../components/ErrorState'
import { useNotifications } from '../../components/NotificationProvider'
import { getErrorMessage } from '../../lib/errorMessages'
import { usePageTitle } from '../../lib/usePageTitle'
import { ActiveLoansList } from './ActiveLoansList'
import { BookPicker } from './BookPicker'
import { CheckoutResult } from './CheckoutResult'
import styles from './IssueReturnPage.module.css'
import { MemberPicker } from './MemberPicker'
import { useCheckout } from './useLoanQueries'

/**
 * Issue & Return screen (architecture 9.2): choose an active member and a book, check out, and
 * return books of a chosen member. A rejected checkout shows the specific reason and keeps the
 * selection; a successful one shows member, book and due date and resets the form (AC-4.FE.1..3).
 */
export function IssueReturnPage() {
  usePageTitle('Issue & Return')
  const { notify } = useNotifications()
  const checkout = useCheckout()
  const [member, setMember] = useState<MemberResponse | null>(null)
  const [book, setBook] = useState<BookResponse | null>(null)
  const [rejection, setRejection] = useState<string | null>(null)
  const [result, setResult] = useState<LoanResponse | null>(null)
  const [returnMember, setReturnMember] = useState<MemberResponse | null>(null)
  // Bumped after a successful checkout so the pickers remount with empty search boxes.
  const [formKey, setFormKey] = useState(0)

  function chooseMember(next: MemberResponse) {
    setMember(next)
    setRejection(null)
  }

  function chooseBook(next: BookResponse) {
    setBook(next)
    setRejection(null)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!member || !book || checkout.isPending) return // a double click must not send twice
    setRejection(null)
    setResult(null)
    checkout.mutate(
      { memberId: member.id, bookId: book.id },
      {
        onSuccess: (loan) => {
          setResult(loan)
          setMember(null)
          setBook(null)
          setFormKey((key) => key + 1)
          notify('Book checked out.', { focus: true })
        },
        onError: (error) => setRejection(getErrorMessage(error)), // the selection stays as it was
      },
    )
  }

  return (
    <>
      <h1 tabIndex={-1}>Issue & Return</h1>

      <section aria-labelledby="issue-heading" className={styles.section}>
        <h2 id="issue-heading">Issue a book</h2>
        <form onSubmit={submit} noValidate>
          <div className={styles.pickers}>
            <MemberPicker key={`member-${formKey}`} selected={member} onSelect={chooseMember} />
            <BookPicker key={`book-${formKey}`} selected={book} onSelect={chooseBook} />
          </div>
          {rejection && <ErrorState message={rejection} />}
          <div className={styles.actions}>
            <button type="submit" className="button button--primary" disabled={!member || !book || checkout.isPending}>
              {checkout.isPending ? 'Checking out…' : 'Check out'}
            </button>
          </div>
        </form>
        {result && <CheckoutResult loan={result} />}
      </section>

      <section aria-labelledby="return-heading" className={styles.section}>
        <h2 id="return-heading">Return a book</h2>
        <div className={styles.returnPicker}>
          <MemberPicker
            selected={returnMember}
            onSelect={setReturnMember}
            searchLabel="Find a member returning books"
            legend="Members"
          />
        </div>
        {returnMember && <ActiveLoansList member={returnMember} />}
      </section>
    </>
  )
}
