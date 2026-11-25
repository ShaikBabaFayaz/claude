import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { formatDate } from '../../lib/dates'
import { ERROR_MESSAGES } from '../../lib/errorMessages'
import { expectNoA11yViolations } from '../../test/axe'
import { makeLoan, makeOverdueLoan } from '../../test/handlers/loans'
import { renderLoanScreen } from '../../test/loanSetup'

const route = '/loans/overdue'

describe('AC-4.FE.5 overdue page', () => {
  it('AC-4.FE.5 shows member, book, due date and days overdue, most overdue first, in text', async () => {
    const loans = [
      makeOverdueLoan(1, { id: 10, member: { id: 2, name: 'Alan Turing' }, book: { id: 2, title: 'Design Patterns', isbn: '9780201633610' } }),
      makeOverdueLoan(6, { id: 11, member: { id: 1, name: 'Ada Lovelace' }, book: { id: 1, title: 'Clean Code', isbn: '9780132350884' } }),
      makeLoan({ id: 12 }), // not overdue: never listed
    ]
    renderLoanScreen({ loans, route })
    expect(screen.getByRole('heading', { level: 1, name: 'Overdue loans' })).toBeInTheDocument()
    expect(document.title).toBe('Overdue loans - Library')

    const table = await screen.findByRole('table', { name: /Overdue loans/ })
    expect(within(table).getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Member',
      'Book',
      'Due date',
      'Days overdue',
    ])
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      'Ada Lovelace',
      'Clean Code',
      formatDate(loans[1].dueDate),
      expect.stringContaining('6 days overdue'),
    ])
    expect(within(rows[1]).getAllByRole('cell')[3]).toHaveTextContent('1 day overdue')
    expect(within(rows[1]).getAllByRole('cell')[0]).toHaveTextContent('Alan Turing')
  })

  it('AC-4.FE.5 has no axe violations', async () => {
    const { container } = renderLoanScreen({ loans: [makeOverdueLoan(3)], route })
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })

  it('AC-4.FE.5 is reached from the "Overdue" navigation link', async () => {
    const user = userEvent.setup()
    const { router } = renderLoanScreen({ loans: [makeOverdueLoan(3)], route: '/loans' })
    await user.click(screen.getByRole('link', { name: 'Overdue' }))
    expect(router.state.location.pathname).toBe(route)
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})

describe('AC-4.FE.6 overdue empty, loading and error states', () => {
  it('AC-4.FE.6 shows "No overdue loans" when nothing is overdue', async () => {
    const { container } = renderLoanScreen({ loans: [makeLoan()], route })
    expect(await screen.findByRole('heading', { level: 2, name: 'No overdue loans' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    await expectNoA11yViolations(container)
  })

  it('AC-4.FE.6 shows a loading indicator while the list loads', async () => {
    renderLoanScreen({
      route,
      overrides: [
        http.get('/api/loans/overdue', async () => {
          await delay(100)
          return HttpResponse.json([])
        }),
      ],
    })
    expect(await screen.findByText('Loading overdue loans…')).toBeInTheDocument()
    expect(await screen.findByText('No overdue loans')).toBeInTheDocument()
  })

  it('AC-4.FE.6 / AC-X.3 shows a friendly error and Retry recovers when the server comes back', async () => {
    const user = userEvent.setup()
    renderLoanScreen({
      loans: [makeOverdueLoan(2)],
      route,
      overrides: [http.get('/api/loans/overdue', () => new Response(null, { status: 502 }), { once: true })],
    })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(ERROR_MESSAGES.BACKEND_UNAVAILABLE)
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })
})
