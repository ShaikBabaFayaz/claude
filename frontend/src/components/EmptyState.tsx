import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

interface EmptyStateProps {
  title: string
  /** Optional explanation under the title. */
  children?: ReactNode
  /** Optional call to action, for example an "Add book" link or a "Clear filters" button. */
  action?: ReactNode
}

/** "Nothing here" panel with an optional next step (AC-1.FE.7, AC-2.FE.5, AC-3.FE.6, AC-4.FE.5). */
export function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      <h2 className={styles.title}>{title}</h2>
      {children && <p className={styles.text}>{children}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}
