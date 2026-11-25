import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { ERROR_MESSAGES } from '../../lib/errorMessages'
import { formatDate } from '../../lib/dates'
import { expectNoA11yViolations } from '../../test/axe'
import { manyBooks } from '../../test/handlers/books'
import { apiErrorResponse } from '../../test/handlers/common'
import { addDays, FAKE_TODAY } from '../../test/handlers/loans'
import { manyMembers } from '../../test/handlers/members'
import { issueSection, renderLoanScreen } from '../../test/loanSetup'
import { server } from '../../test/server'

const checkoutButton = () => issueSection().getByRole('button', { name: /^Check out/ })

async function pick(user: ReturnType<typeof userEvent.setup>, member: RegExp, book: RegExp) {
  await user.click(await issueSection().findByRole('radio', { name: member }))
  await user.click(await issueSection().findByRole('radio', { name: book }))
}

describe('AC-4.FE.1 issue screen pickers', () => {
  it('AC-4.FE.1 offers only active members and books with availability, and enables checkout once both are chosen', async () => {
    const user = userEvent.setup()
    const { memberApi } = renderLoanScreen()
    expect(screen.getByRole('heading', { level: 1, name: 'Issue & Return' })).toBeInTheDocument()
    expect(document.title).toBe('Issue & Return - Library')

    await issueSection().findByRole('radio', { name: /Ada Lovelace/ })
    const memberNames = issueSection()
      .getAllByRole('radio')
      .map((radio) => radio.closest('label')?.textContent ?? '')
    expect(memberNames.some((text) => text.includes('Grace Hopper'))).toBe(false) // inactive
    expect(memberApi.requests.some((request) => request.url.includes('status=ACTIVE'))).toBe(true)
    await issueSection().findByRole('radio', { name: /Clean Code/ })
    expect(issueSection().getAllByText('Available (2)')).toHaveLength(2)
    expect(issueSection().getByText('All copies out')).toBeInTheDocument()

    expect(checkoutButton()).toBeDisabled()
    await user.click(issueSection().getByRole('radio', { name: /Ada Lovelace/ }))
    expect(checkoutButton()).toBeDisabled()
    await user.click(issueSection().getByRole('radio', { name: /Clean Code/ }))
    expect(checkoutButton()).toBeEnabled()
    expect(issueSection().getByText('Selected member: Ada Lovelace')).toBeInTheDocument()
    expect(issueSection().getByText('Selected book: Clean Code')).toBeInTheDocument()
  })

  it('AC-4.FE.1 searches members after a typing pause and selects with the keyboard alone', async () => {
    const user = userEvent.setup()
    const { memberApi } = renderLoanScreen()
    await issueSection().findByRole('radio', { name: /Ada Lovelace/ })

    await user.type(issueSection().getByLabelText('Find a member'), 'alan')
    await waitFor(() => expect(memberApi.requests.at(-1)?.url).toContain('search=alan'))
    await waitFor(() => expect(issueSection().queryByRole('radio', { name: /Ada Lovelace/ })).not.toBeInTheDocument())
    const alan = await issueSection().findByRole('radio', { name: /Alan Turing/ })

    alan.focus()
    await user.keyboard(' ')
    expect(alan).toBeChecked()
    expect(issueSection().getByText('Selected member: Alan Turing')).toBeInTheDocument()
  })

  it('AC-4.FE.1 says "Showing 20 of N - refine your search" in a live region when there are more matches (DR-19)', async () => {
    renderLoanScreen({ members: manyMembers(45), books: manyBooks(45) })
    await issueSection().findByText('Showing 20 of 23 - refine your search') // 23 active members
    await issueSection().findByText('Showing 20 of 45 - refine your search')
    expect(issueSection().getByText('Showing 20 of 45 - refine your search')).toHaveAttribute('role', 'status')
    expect(issueSection().getAllByRole('radio')).toHaveLength(40)
  })

  it('AC-4.FE.1 shows a message when no member matches', async () => {
    const user = userEvent.setup()
    renderLoanScreen()
    await issueSection().findByRole('radio', { name: /Ada Lovelace/ })
    await user.type(issueSection().getByLabelText('Find a member'), 'zzzz')
    expect(await issueSection().findByText('No active member matches your search.')).toBeInTheDocument()
  })

  it('AC-X.3 shows a friendly error with Retry when the member list cannot be loaded', async () => {
    renderLoanScreen({ overrides: [http.get('/api/members', () => new Response(null, { status: 502 }))] })
    expect(await issueSection().findByText(ERROR_MESSAGES.BACKEND_UNAVAILABLE)).toBeInTheDocument()
    expect(issueSection().getAllByRole('button', { name: 'Retry' }).length).toBeGreaterThan(0)
  })

  it('NFR-3 has no axe violations with a selection made', async () => {
    const user = userEvent.setup()
    const { container } = renderLoanScreen()
    await pick(user, /Alan Turing/, /Clean Code/)
    await expectNoA11yViolations(container)
  })
})

describe('AC-4.FE.2 successful checkout', () => {
  it('AC-4.FE.2 shows member, book and due date, resets the selection and moves focus to the success notice', async () => {
    const user = userEvent.setup()
    const { loanApi, bookApi } = renderLoanScreen()
    await pick(user, /Alan Turing/, /The Pragmatic Programmer/)
    await user.click(checkoutButton())

    const result = await screen.findByRole('region', { name: 'Book checked out' })
    expect(within(result).getByText('Alan Turing')).toBeInTheDocument()
    expect(within(result).getByText('The Pragmatic Programmer')).toBeInTheDocument()
    expect(within(result).getByText(formatDate(addDays(FAKE_TODAY, 14)))).toBeInTheDocument()
    expect(loanApi.requests.filter((request) => request.method === 'POST')).toHaveLength(1)
    expect(loanApi.requests[0].body).toEqual({ memberId: 2, bookId: 3 })

    expect(issueSection().getByText('No member selected.')).toBeInTheDocument()
    expect(issueSection().getByText('No book selected.')).toBeInTheDocument()
    expect(checkoutButton()).toBeDisabled()
    expect(screen.getByText('Book checked out.').closest('[role="status"]')).toHaveFocus()

    // Caches were refreshed: the book now shows one copy fewer.
    expect(bookApi.books.find((book) => book.id === 3)?.availableCopies).toBe(1)
    expect(await issueSection().findByText('Available (1)')).toBeInTheDocument()
  })

  it('AC-4.FE.2 does not send a second request on a double click', async () => {
    const user = userEvent.setup()
    const { loanApi } = renderLoanScreen()
    await pick(user, /Alan Turing/, /Clean Code/)
    await user.dblClick(checkoutButton())
    await screen.findByRole('region', { name: 'Book checked out' })
    expect(loanApi.requests.filter((request) => request.method === 'POST')).toHaveLength(1)
  })

  it('NFR-3 has no axe violations after a checkout', async () => {
    const user = userEvent.setup()
    const { container } = renderLoanScreen()
    await pick(user, /Alan Turing/, /Clean Code/)
    await user.click(checkoutButton())
    await screen.findByRole('region', { name: 'Book checked out' })
    await expectNoA11yViolations(container)
  })
})

describe('AC-4.FE.3 rejected checkout', () => {
  const rejections = [
    ['LOAN_LIMIT_REACHED', 409],
    ['NO_COPY_AVAILABLE', 409],
    ['MEMBER_INACTIVE', 409],
    ['DUPLICATE_ACTIVE_LOAN', 409],
    ['CONCURRENT_UPDATE', 409],
    ['MEMBER_NOT_FOUND', 404],
    ['BOOK_NOT_FOUND', 404],
  ] as const

  it.each(rejections)('AC-4.FE.3 %s shows its plain-language reason and keeps the selection', async (code, status) => {
    const user = userEvent.setup()
    renderLoanScreen()
    await pick(user, /Alan Turing/, /Clean Code/)
    server.use(http.post('/api/loans', () => apiErrorResponse(status, code, 'raw server text')))
    await user.click(checkoutButton())

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(ERROR_MESSAGES[code])
    expect(alert).not.toHaveTextContent('raw server text')
    expect(issueSection().getByText('Selected member: Alan Turing')).toBeInTheDocument()
    expect(issueSection().getByText('Selected book: Clean Code')).toBeInTheDocument()
    expect(checkoutButton()).toBeEnabled()
    expect(screen.queryByRole('region', { name: 'Book checked out' })).not.toBeInTheDocument()
  })

  it('AC-4.FE.3 a book with no free copy is rejected by the server with the specific reason', async () => {
    const user = userEvent.setup()
    renderLoanScreen()
    await pick(user, /Alan Turing/, /Design Patterns/)
    await user.click(checkoutButton())
    expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_MESSAGES.NO_COPY_AVAILABLE)
    expect(issueSection().getByText('Selected book: Design Patterns')).toBeInTheDocument()
  })

  it('AC-X.3 shows a friendly message when the server cannot be reached during checkout', async () => {
    const user = userEvent.setup()
    renderLoanScreen()
    await pick(user, /Alan Turing/, /Clean Code/)
    server.use(http.post('/api/loans', () => new Response(null, { status: 502 })))
    await user.click(checkoutButton())
    expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_MESSAGES.BACKEND_UNAVAILABLE)
    expect(checkoutButton()).toBeEnabled()
  })
})
