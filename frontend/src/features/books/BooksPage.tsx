import { useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import type { BookFilters as BookFilterValues, BookResponse } from '../../api/types'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { useNotifications } from '../../components/NotificationProvider'
import { Pagination } from '../../components/Pagination'
import { getErrorMessage } from '../../lib/errorMessages'
import { usePageTitle } from '../../lib/usePageTitle'
import { BookFilters } from './BookFilters'
import styles from './BooksPage.module.css'
import { BookTable } from './BookTable'
import {
  bookFiltersToSearchParams,
  hasActiveBookFilters,
  parseBookFilters,
  useBooksList,
  useDeleteBook,
} from './useBookQueries'

/** "1 book found", "12 books found" (AC-2.FE.2). Not "No books found": that sentence is the empty-result panel's. */
function matchCountText(totalElements: number): string {
  return `${totalElements} ${totalElements === 1 ? 'book' : 'books'} found`
}

/**
 * The combined Books screen (architecture 9.2, D8): filter bar, match count, catalogue table with
 * availability badges, pagination, and add/edit/delete. The filters and the page live in the URL
 * query string, read through `parseBookFilters`, so pagination keeps filters and Back or reload
 * restore the same view (AC-2.FE.4).
 */
export function BooksPage() {
  usePageTitle('Books')
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => parseBookFilters(searchParams), [searchParams])
  const filtersApplied = hasActiveBookFilters(filters)
  const query = useBooksList(filters)
  const { notify } = useNotifications()
  const deleteBook = useDeleteBook()
  const [deleteTarget, setDeleteTarget] = useState<BookResponse | null>(null)
  const titleInputRef = useRef<HTMLInputElement>(null)

  /** A change of filters starts again at the first page. */
  function applyFilters(next: BookFilterValues) {
    setSearchParams(bookFiltersToSearchParams({ ...next, page: undefined }))
  }

  function goToPage(page: number) {
    setSearchParams(bookFiltersToSearchParams({ ...filters, page: page > 0 ? page : undefined }))
  }

  function clearFilters() {
    setSearchParams(bookFiltersToSearchParams({}))
    // The Clear button disappears with the filters; keep focus in the filter bar instead of on <body>.
    titleInputRef.current?.focus()
  }

  function confirmDelete() {
    if (!deleteTarget) return
    const book = deleteTarget
    deleteBook.mutate(book.id, {
      onSuccess: () => {
        setDeleteTarget(null)
        // The row is gone, so focus moves to the notice instead of falling back to <body> (DR-11).
        notify(`"${book.title}" was deleted.`, { focus: true })
      },
      onError: (error) => {
        setDeleteTarget(null)
        notify(getErrorMessage(error), { kind: 'error', focus: true })
      },
    })
  }

  const data = query.data
  const noResults = !query.isError && data !== undefined && data.totalElements === 0
  const isEmptyCatalogue = noResults && !filtersApplied && !query.isPlaceholderData
  const isNoMatch = noResults && filtersApplied && !query.isPlaceholderData

  let content: ReactNode
  if (query.isError) {
    content = <ErrorState error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />
  } else if (query.isPending || (noResults && query.isPlaceholderData)) {
    content = <LoadingState label="Loading books…" />
  } else if (isNoMatch) {
    content = (
      <EmptyState
        title="No books found"
        action={
          <button type="button" className="button button--primary" onClick={clearFilters}>
            Clear filters
          </button>
        }
      >
        No book matches these filters. Change the search or clear the filters to see every book.
      </EmptyState>
    )
  } else if (isEmptyCatalogue) {
    content = (
      <EmptyState
        title="No books in the catalogue yet"
        action={
          <Link to="/books/new" className="button button--primary">
            Add book
          </Link>
        }
      >
        Add the first book to start building the catalogue.
      </EmptyState>
    )
  } else if (query.data.content.length === 0) {
    // A page number beyond the last page, for example from an old link or after deleting the last rows.
    content = (
      <EmptyState
        title="There are no books on this page"
        action={
          <button type="button" className="button button--primary" onClick={() => goToPage(0)}>
            Go to the first page
          </button>
        }
      >
        The list has fewer pages than this address asks for.
      </EmptyState>
    )
  } else {
    content = (
      <div aria-busy={query.isPlaceholderData} className={query.isPlaceholderData ? styles.stale : undefined}>
        <BookTable books={query.data.content} onDelete={setDeleteTarget} />
        <Pagination page={query.data.page} totalPages={query.data.totalPages} onPageChange={goToPage} />
      </div>
    )
  }

  return (
    <>
      <div className={styles.header}>
        <h1 tabIndex={-1}>Books</h1>
        {!isEmptyCatalogue && (
          <Link to="/books/new" className="button button--primary">
            Add book
          </Link>
        )}
      </div>
      <BookFilters
        filters={filters}
        onChange={applyFilters}
        onClear={clearFilters}
        showClear={filtersApplied && !isNoMatch}
        titleInputRef={titleInputRef}
      />
      {/* Always rendered so screen readers announce the new count after a search (DR-11, WCAG 4.1.3). */}
      <p role="status" className={styles.count}>
        {data && !query.isError ? matchCountText(data.totalElements) : ''}
      </p>
      {content}
      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `Delete "${deleteTarget.title}"?` : 'Delete book?'}
        confirmLabel="Delete book"
        danger
        busy={deleteBook.isPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={confirmDelete}
      >
        This removes the book from the catalogue. This cannot be undone.
      </ConfirmDialog>
    </>
  )
}
