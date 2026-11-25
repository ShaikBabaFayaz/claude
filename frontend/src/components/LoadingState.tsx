import styles from './LoadingState.module.css'

interface LoadingStateProps {
  /** Accessible text of the indicator; also shown on screen. */
  label?: string
}

/** Loading indicator with text, announced politely to screen readers (architecture 9.4). */
export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return (
    <div role="status" aria-live="polite" className={styles.loading}>
      <span className={styles.spinner} aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
