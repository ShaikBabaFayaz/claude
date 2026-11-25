import styles from './Pagination.module.css'

interface PaginationProps {
  /** Zero-based current page, as the API uses it. */
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

/**
 * Previous/Next navigation with "Page x of y" (architecture 9.1). Renders nothing when there is
 * only one page or none. The text is one-based for people; the callback receives the zero-based index.
 */
export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null
  const isFirst = page <= 0
  const isLast = page >= totalPages - 1

  return (
    <nav aria-label="Pagination" className={styles.pagination}>
      <button type="button" className="button" disabled={isFirst} onClick={() => onPageChange(page - 1)}>
        Previous
      </button>
      <span className={styles.position}>
        Page {page + 1} of {totalPages}
      </span>
      <button type="button" className="button" disabled={isLast} onClick={() => onPageChange(page + 1)}>
        Next
      </button>
    </nav>
  )
}
