import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationProvider } from '../components/NotificationProvider'
import { expectNoA11yViolations } from '../test/axe'
import { createTestQueryClient, renderWithProviders } from '../test/render'
import { AppLayout } from './AppLayout'
import { RouteErrorBoundary } from './RouteErrorBoundary'

let shouldThrow = true

function Fragile() {
  if (shouldThrow) throw new Error('internal detail that must not be shown: SELECT * FROM book')
  return <p>Page content</p>
}

describe('RouteErrorBoundary (AC-X.3)', () => {
  beforeEach(() => {
    shouldThrow = true
    // React and the boundary log the caught error; keep the test output readable.
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('AC-X.3 shows a friendly fallback instead of a blank screen, without raw error text', () => {
    renderWithProviders(
      <RouteErrorBoundary>
        <Fragile />
      </RouteErrorBoundary>,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Something went wrong' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong while showing this page')
    expect(screen.queryByText(/SELECT/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to Books' })).toBeInTheDocument()
  })

  it('moves focus to the fallback heading', () => {
    renderWithProviders(
      <RouteErrorBoundary>
        <Fragile />
      </RouteErrorBoundary>,
    )
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus()
  })

  it('AC-X.3 Retry re-renders the page and recovers when the problem is gone', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <RouteErrorBoundary>
        <Fragile />
      </RouteErrorBoundary>,
    )
    shouldThrow = false
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByText('Page content')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the fallback again when Retry fails again', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <RouteErrorBoundary>
        <Fragile />
      </RouteErrorBoundary>,
    )
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('inside the layout, keeps the navigation usable and clears the error when navigating away', async () => {
    const router = createMemoryRouter(
      [
        {
          path: '/',
          element: <AppLayout />,
          children: [
            { path: 'bad', element: <Fragile /> },
            { path: 'books', element: <p>Books content</p> },
          ],
        },
      ],
      { initialEntries: ['/bad'] },
    )
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <NotificationProvider>
          <RouterProvider router={router} />
        </NotificationProvider>
      </QueryClientProvider>,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
    // The navigation survives a page crash, so the user is never stuck on a blank screen.
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()

    shouldThrow = false
    await act(() => router.navigate('/books'))
    expect(screen.getByText('Books content')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('has no axe violations in the fallback', async () => {
    const { container } = renderWithProviders(
      <RouteErrorBoundary>
        <Fragile />
      </RouteErrorBoundary>,
    )
    await expectNoA11yViolations(container)
  })
})
