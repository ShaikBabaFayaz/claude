import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http as mswHttp, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { useQuery } from '@tanstack/react-query'
import { http } from '../api/http'
import { ApiError } from '../api/http'
import { expectNoA11yViolations } from '../test/axe'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { ErrorState } from './ErrorState'
import { LoadingState } from './LoadingState'

const unavailable = new ApiError({ status: 502, code: 'BACKEND_UNAVAILABLE', message: 'raw proxy text' })

describe('ErrorState (AC-X.3)', () => {
  it('AC-X.3 shows the friendly backend-unavailable message and a Retry button, never raw text', () => {
    render(<ErrorState error={unavailable} onRetry={() => {}} />)
    expect(screen.getByRole('alert')).toHaveTextContent(
      'The library server cannot be reached. Check that it is running, then try again.',
    )
    expect(screen.queryByText(/raw proxy text/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('AC-X.3 calls onRetry when Retry is pressed', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    render(<ErrorState error={unavailable} onRetry={onRetry} />)
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('maps other errors to plain language, and non-API errors to a generic sentence', () => {
    const { rerender } = render(
      <ErrorState error={new ApiError({ status: 404, code: 'BOOK_NOT_FOUND', message: 'x' })} />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('This book no longer exists. It may have been deleted.')
    rerender(<ErrorState error={new TypeError('Cannot read properties of undefined')} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong. Please try again.')
    expect(screen.queryByText(/Cannot read/)).not.toBeInTheDocument()
  })

  it('shows no Retry button without onRetry (for example a 404), and renders extra actions', () => {
    render(
      <ErrorState error={new ApiError({ status: 404, code: 'BOOK_NOT_FOUND', message: 'x' })}>
        <a href="/books">Back to Books</a>
      </ErrorState>,
    )
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to Books' })).toBeInTheDocument()
  })

  it('disables the button and says so while retrying', () => {
    render(<ErrorState error={unavailable} onRetry={() => {}} retrying />)
    expect(screen.getByRole('button', { name: 'Retrying…' })).toBeDisabled()
  })

  it('has no axe violations', async () => {
    const { container } = render(<ErrorState error={unavailable} onRetry={() => {}} />)
    await expectNoA11yViolations(container)
  })
})

describe('ErrorState wired to a query refetch (AC-X.3)', () => {
  function BooksProbe() {
    const query = useQuery({ queryKey: ['probe'], queryFn: () => http.get<{ ok: boolean }>('/probe') })
    if (query.isPending) return <LoadingState />
    if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
    return <p>Loaded</p>
  }

  it('AC-X.3 shows loading, then the error for a proxy 502, and Retry works once the backend is back', async () => {
    const user = userEvent.setup()
    let backendUp = false
    server.use(
      mswHttp.get('/api/probe', () => (backendUp ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status: 502 }))),
    )
    renderWithProviders(<BooksProbe />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByRole('alert')).toHaveTextContent('The library server cannot be reached')

    backendUp = true
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Loaded')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
