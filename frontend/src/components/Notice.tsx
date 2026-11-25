import type { ReactNode, Ref } from 'react'
import styles from './Notice.module.css'

export type NoticeKind = 'success' | 'info' | 'error'

const LABELS: Record<NoticeKind, string> = {
  success: 'Success',
  info: 'Notice',
  error: 'Problem',
}

interface NoticeProps {
  kind?: NoticeKind
  /** The message. When empty the component renders an empty, persistent live region. */
  children?: ReactNode
  onDismiss?: () => void
  /** Focusable (`tabIndex=-1`) so the app can move focus here after an action removed a row (DR-11). */
  ref?: Ref<HTMLDivElement>
}

/**
 * Success/info/error region (`role="status"`, `aria-live="polite"`; architecture 9.1, 9.4). The
 * region element is always rendered so screen readers announce text that is added later; the kind
 * is conveyed with a text label as well as colour.
 */
export function Notice({ kind = 'info', children, onDismiss, ref }: NoticeProps) {
  const hasMessage = children !== undefined && children !== null && children !== false && children !== ''
  return (
    <div
      ref={ref}
      role="status"
      aria-live="polite"
      tabIndex={-1}
      className={hasMessage ? `${styles.notice} ${styles[kind]}` : styles.empty}
    >
      {hasMessage && (
        <>
          <p className={styles.message}>
            <strong className={styles.label}>{LABELS[kind]}:</strong> {children}
          </p>
          {onDismiss && (
            <button type="button" className="button button--small" onClick={onDismiss}>
              Dismiss
            </button>
          )}
        </>
      )}
    </div>
  )
}
