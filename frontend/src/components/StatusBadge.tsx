import type { ReactNode } from 'react'
import styles from './StatusBadge.module.css'

export type BadgeTone = 'success' | 'danger' | 'warning' | 'neutral'

/** A different shape per tone, so the state is distinguishable without colour (WCAG 1.4.1). */
const SHAPES: Record<BadgeTone, string> = {
  success: '✓', // check mark
  danger: '✕', // multiplication x
  warning: '▲', // triangle
  neutral: '●', // circle
}

interface StatusBadgeProps {
  tone: BadgeTone
  /** The state as words, for example "Available (2)", "All copies out", "Active", "Overdue by 6 days". */
  children: ReactNode
}

/** State pill: always text first, plus a shape and a colour (architecture 9.4, NFR-3). */
export function StatusBadge({ tone, children }: StatusBadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[tone]}`}>
      <span aria-hidden="true" className={styles.shape}>
        {SHAPES[tone]}
      </span>
      {children}
    </span>
  )
}
