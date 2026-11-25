import type { ReactNode } from 'react'
import { getErrorMessage } from '../lib/errorMessages'
import styles from './ErrorState.module.css'

interface ErrorStateProps {
  /** The thrown value; shown as plain language, never as raw error text (AC-X.3). */
  error?: unknown
  /** Explicit text, used instead of `error` when the caller already has a friendly message. */
  message?: string
  /** Retry action, typically the query's `refetch`. Omit to show no button (for example a 404). */
  onRetry?: () => void
  /** True while the retry is running. */
  retrying?: boolean
  /** Extra actions, for example a link back to the list. */
  children?: ReactNode
}

/** Friendly failure panel with a Retry button (AC-X.3, architecture 9.3). */
export function ErrorState({ error, message, onRetry, retrying = false, children }: ErrorStateProps) {
  return (
    <div role="alert" className={styles.error}>
      <p className={styles.message}>
        <span aria-hidden="true">{'⚠'} </span>
        {message ?? getErrorMessage(error)}
      </p>
      <div className={styles.actions}>
        {onRetry && (
          <button type="button" className="button button--primary" disabled={retrying} onClick={onRetry}>
            {retrying ? 'Retrying…' : 'Retry'}
          </button>
        )}
        {children}
      </div>
    </div>
  )
}
