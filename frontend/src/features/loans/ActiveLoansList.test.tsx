import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { formatDate } from '../../lib/dates'
import { ERROR_MESSAGES } from '../../lib/errorMessages'
import { expectNoA11yViolations } from '../../test/axe'
import { apiErrorResponse } from '../../test/handlers/common'
import { makeLoan, makeOverdueLoan } from '../../test/handlers/loans'
import { renderLoanScreen, returnSection } from '../../test/loanSetup'
import { server } from '../../test/server'

/** Ada has one overdue loan (Clean Code, 6 days) and one on time (Pragmatic Programmer). */
function adaLoans() {
  return [
    makeOverdueLoan(6, { id: 1, book: { id: 1, title: 'Clean Code', isbn: '9780132350884' } }),
    makeLoan({ id: 2, book: { id: 3, title: 'The Pragmatic Programmer', isbn: '9780135957059' } }),
  ]
}

async function openAdasLoans(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await returnSection().findByRole('radio', { name: /Ada Lovelace/ }))
  return returnSection().findByRole('table', { name: 'Books on loan to Ada Lovelace' })
}

const writes = (api: { requests: Array<{ method: string; url: string }> }) =>
  api.requests.filter((request) => request.method === 'POST').map((request) => request.url)

describe('AC-4.FE.4 active loans of the selected member', () => {
  it('AC-4.FE.4 shows book, checkout date, due date and a Return action, and flags overdue loans by text', async () => {
    const user = userEvent.setup()
    const { loanApi } = renderLoanScreen({ loans: adaLoans() })
    const table = await openAdasLoans(user)

    expect(loanApi.requests.at(-1)?.url).toBe('/api/loans/active?memberId=1')
    expect(within(table).getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Book',
      'Checkout date',
      'Due date',
      'Status',
      'Actions',
    ])
    const overdue = within(table).getByRole('cell', { name: 'Clean Code' }).closest('tr') as HTMLElement
    expect(within(overdue).getByText(formatDate(adaLoans()[0].checkoutDate))).toBeInTheDocument()
    expect(within(overdue).getByText(formatDate(adaLoans()[0].dueDate))).toBeInTheDocument()
    expect(within(overdue).getByText('Overdue by 6 days')).toBeInTheDocument()
    expect(within(overdue).getByRole('button', { name: 'Return Clean Code' })).toBeInTheDocument()
    const onTime = within(table).getByRole('cell', { name: 'The Pragmatic Programmer' }).closest('tr') as HTMLElement
    expect(within(onTime).queryByText(/Overdue/)).not.toBeInTheDocument()
    expect(within(onTime).getByText('On time')).toBeInTheDocument()
  })

  it('AC-4.FE.4 says so when the member has no books on loan', async () => {
    const user = userEvent.setup()
    renderLoanScreen({ loans: [] })
    await user.click(await returnSection().findByRole('radio', { name: /Alan Turing/ }))
    expect(await returnSection().findByText('Alan Turing has no books on loan.')).toBeInTheDocument()
  })

  it('AC-4.FE.4 asks for confirmation; Cancel leaves the loan untouched', async () => {
    const user = userEvent.setup()
    const { loanApi } = renderLoanScreen({ loans: adaLoans() })
    const table = await openAdasLoans(user)
    const trigger = within(table).getByRole('button', { name: 'Return Clean Code' })
    await user.click(trigger)

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: 'Return "Clean Code"?' })).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(writes(loanApi)).toEqual([])
    expect(within(table).getByRole('cell', { name: 'Clean Code' })).toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('AC-4.FE.4 after confirming, the loan disappears, a success notice is shown and receives focus', async () => {
    const user = userEvent.setup()
    const { loanApi, bookApi } = renderLoanScreen({ loans: adaLoans() })
    const table = await openAdasLoans(user)
    await user.click(within(table).getByRole('button', { name: 'Return Clean Code' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Return book' }))

    const notice = (await screen.findByText(/"Clean Code" was returned by Ada Lovelace\./)).closest('[role="status"]')
    expect(notice).toHaveFocus() // the Return button was removed with its row (DR-11)
    expect(writes(loanApi)).toEqual(['/api/loans/1/return'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.queryByRole('cell', { name: 'Clean Code' })).not.toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'The Pragmatic Programmer' })).toBeInTheDocument()
    expect(bookApi.books.find((book) => book.id === 1)?.availableCopies).toBe(3)
  })

  it('AC-4.FE.4 a loan returned elsewhere (409 LOAN_ALREADY_RETURNED) shows the reason and the list is refreshed', async () => {
    const user = userEvent.setup()
    const { loanApi } = renderLoanScreen({ loans: adaLoans() })
    const table = await openAdasLoans(user)
    loanApi.loans[0].returnDate = '2026-11-24' // returned by someone else after this list was loaded
    await user.click(within(table).getByRole('button', { name: 'Return Clean Code' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Return book' }))

    expect(await screen.findByText(ERROR_MESSAGES.LOAN_ALREADY_RETURNED)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('cell', { name: 'Clean Code' })).not.toBeInTheDocument())
  })

  it('AC-4.FE.4 a missing loan (404 LOAN_NOT_FOUND) shows the reason', async () => {
    const user = userEvent.setup()
    renderLoanScreen({ loans: adaLoans() })
    const table = await openAdasLoans(user)
    server.use(http.post('/api/loans/:id/return', () => apiErrorResponse(404, 'LOAN_NOT_FOUND', 'raw')))
    await user.click(within(table).getByRole('button', { name: 'Return Clean Code' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Return book' }))
    expect(await screen.findByText(ERROR_MESSAGES.LOAN_NOT_FOUND)).toBeInTheDocument()
  })

  it('AC-X.3 shows a friendly error with Retry when the loans cannot be loaded', async () => {
    const user = userEvent.setup()
    renderLoanScreen({
      loans: adaLoans(),
      overrides: [http.get('/api/loans/active', () => new Response(null, { status: 502 }))],
    })
    await user.click(await returnSection().findByRole('radio', { name: /Ada Lovelace/ }))
    expect(await returnSection().findByText(ERROR_MESSAGES.BACKEND_UNAVAILABLE)).toBeInTheDocument()
    expect(returnSection().getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('NFR-3 has no axe violations with the loans table shown', async () => {
    const user = userEvent.setup()
    const { container } = renderLoanScreen({ loans: adaLoans() })
    await openAdasLoans(user)
    await expectNoA11yViolations(container)
  })
})
