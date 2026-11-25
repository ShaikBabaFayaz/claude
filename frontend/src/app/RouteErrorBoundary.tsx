import { Component, useEffect, useRef, type ErrorInfo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ErrorState } from '../components/ErrorState'

const RENDER_ERROR_MESSAGE = 'Something went wrong while showing this page. Try again, or go back to Books.'

function RenderErrorFallback({ onRetry }: { onRetry: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    headingRef.current?.focus()
  }, [])
  return (
    <>
      <h1 ref={headingRef} tabIndex={-1}>
        Something went wrong
      </h1>
      <ErrorState message={RENDER_ERROR_MESSAGE} onRetry={onRetry}>
        <Link to="/books">Go to Books</Link>
      </ErrorState>
    </>
  )
}

interface RouteErrorBoundaryProps {
  /** When this value changes (for example the pathname) a shown error is cleared. */
  resetKey?: string
  children: ReactNode
}

interface RouteErrorBoundaryState {
  failed: boolean
}

/**
 * Catches errors thrown while rendering a page and shows a friendly fallback with a Retry button
 * instead of a blank screen (AC-X.3, architecture 9.1). Retry re-renders the page; navigating to
 * another route also clears the error.
 */
export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    console.error('Page render failed', error, info.componentStack)
  }

  componentDidUpdate(previous: RouteErrorBoundaryProps) {
    if (this.state.failed && previous.resetKey !== this.props.resetKey) this.setState({ failed: false })
  }

  private retry = () => this.setState({ failed: false })

  render() {
    if (this.state.failed) return <RenderErrorFallback onRetry={this.retry} />
    return this.props.children
  }
}

/**
 * Router-level `errorElement` for failures outside the page area (for example in the layout
 * itself). Kept free of router hooks and shared components that could fail the same way.
 */
export function RootErrorElement() {
  return (
    <main style={{ padding: '2rem' }}>
      <h1>Something went wrong</h1>
      <p>The application could not be displayed. Reload the page to try again.</p>
      <button type="button" className="button button--primary" onClick={() => window.location.reload()}>
        Reload page
      </button>
    </main>
  )
}
