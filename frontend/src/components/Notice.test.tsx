import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { renderWithProviders } from '../test/render'
import { EmptyState } from './EmptyState'
import { LoadingState } from './LoadingState'
import { Notice } from './Notice'
import { NotificationRegion, useNotifications } from './NotificationProvider'

function Notifier() {
  const { notify } = useNotifications()
  return (
    <>
      <button type="button" onClick={() => notify('Book saved.')}>
        Save
      </button>
      <button type="button" onClick={() => notify('Book deleted.', { focus: true })}>
        Delete
      </button>
      <button type="button" onClick={() => notify('Heads up.', { kind: 'info' })}>
        Info
      </button>
      <NotificationRegion />
    </>
  )
}

describe('Notice and NotificationProvider (AC-1.FE.6, DR-11)', () => {
  it('renders an empty persistent role="status" live region before any notice exists', () => {
    renderWithProviders(<Notifier />)
    const region = screen.getByRole('status')
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toBeEmptyDOMElement()
  })

  it('announces a success notice in the same region, labelled in text', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Notifier />)
    const region = screen.getByRole('status')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('status')).toBe(region)
    expect(region).toHaveTextContent('Success: Book saved.')
  })

  it('moves focus to the notice only when asked (after a row was removed)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Notifier />)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(screen.getByRole('status')).not.toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('replaces the previous notice and supports other kinds', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Notifier />)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await user.click(screen.getByRole('button', { name: 'Info' }))
    expect(screen.getByRole('status')).toHaveTextContent('Notice: Heads up.')
    expect(screen.queryByText(/Book saved/)).not.toBeInTheDocument()
  })

  it('can be dismissed with a button', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Notifier />)
    await user.click(screen.getByRole('button', { name: 'Save' }))
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('fails with a clear message when used outside the provider', () => {
    expect(() => render(<NotificationRegion />)).toThrow(/NotificationProvider/)
  })

  it('has no axe violations with and without a notice, for every kind', async () => {
    const { container, rerender } = render(<Notice />)
    await expectNoA11yViolations(container)
    for (const kind of ['success', 'info', 'error'] as const) {
      rerender(
        <Notice kind={kind} onDismiss={() => {}}>
          Something happened.
        </Notice>,
      )
      await expectNoA11yViolations(container)
    }
  })
})

describe('LoadingState and EmptyState (AC-1.FE.7, AC-2.FE.5)', () => {
  it('LoadingState exposes its text in a polite live region', async () => {
    const { container } = render(<LoadingState label="Loading books" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading books')
    await expectNoA11yViolations(container)
  })

  it('EmptyState shows a heading, explanation and call to action', async () => {
    const { container } = render(
      <EmptyState title="No books yet" action={<a href="/books/new">Add book</a>}>
        The catalogue is empty.
      </EmptyState>,
    )
    expect(screen.getByRole('heading', { level: 2, name: 'No books yet' })).toBeInTheDocument()
    expect(screen.getByText('The catalogue is empty.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Add book' })).toBeInTheDocument()
    await expectNoA11yViolations(container)
  })
})
