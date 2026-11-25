import { Link, useNavigate, useParams } from 'react-router'
import { isApiError } from '../../api/http'
import type { BookRequest, BookResponse } from '../../api/types'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { useNotifications } from '../../components/NotificationProvider'
import { usePageTitle } from '../../lib/usePageTitle'
import { BookForm, type BookFormValues } from './BookForm'
import { useBook, useCreateBook, useUpdateBook } from './useBookQueries'

interface BookFormPageProps {
  mode: 'create' | 'edit'
}

/** `/books/new` and `/books/:id/edit` (architecture 9.2). */
export function BookFormPage({ mode }: BookFormPageProps) {
  return mode === 'create' ? <CreateBookPage /> : <EditBookPage />
}

function CreateBookPage() {
  usePageTitle('Add book')
  const navigate = useNavigate()
  const { notify } = useNotifications()
  const createBook = useCreateBook()

  async function submit(request: BookRequest) {
    const created = await createBook.mutateAsync(request)
    notify(`"${created.title}" was added to the catalogue.`)
    await navigate('/books')
  }

  return (
    <>
      <h1 tabIndex={-1}>Add book</h1>
      <BookForm submitLabel="Add book" onSubmit={submit} />
    </>
  )
}

function toFormValues(book: BookResponse): BookFormValues {
  return {
    title: book.title,
    author: book.author,
    isbn: book.isbn,
    totalCopies: String(book.totalCopies),
  }
}

function BackToBooks() {
  return <Link to="/books">Back to Books</Link>
}

function EditBookPage() {
  usePageTitle('Edit book')
  const { id } = useParams()
  // A non-numeric id can never match a book: treat it as "not found" without calling the API.
  const bookId = /^\d+$/.test(id ?? '') ? Number(id) : null

  return (
    <>
      <h1 tabIndex={-1}>Edit book</h1>
      {bookId === null ? (
        <ErrorState message="This book could not be found. It may have been deleted.">
          <BackToBooks />
        </ErrorState>
      ) : (
        <EditBookLoader bookId={bookId} />
      )}
    </>
  )
}

function EditBookLoader({ bookId }: { bookId: number }) {
  const query = useBook(bookId)

  if (query.isError) {
    // A 4xx (for example a deleted book) will not change on retry, so no Retry button (DR-19).
    const retryable = !isApiError(query.error) || query.error.status >= 500 || query.error.isBackendUnavailable
    return (
      <ErrorState error={query.error} onRetry={retryable ? () => void query.refetch() : undefined} retrying={query.isFetching}>
        <BackToBooks />
      </ErrorState>
    )
  }
  // Wait for a fetch made after this page opened, so the form never starts from a stale cached copy.
  if (query.isPending || !query.isFetchedAfterMount) return <LoadingState label="Loading book…" />
  return <EditBookForm book={query.data} />
}

function EditBookForm({ book }: { book: BookResponse }) {
  const navigate = useNavigate()
  const { notify } = useNotifications()
  const updateBook = useUpdateBook(book.id)

  async function submit(request: BookRequest) {
    const saved = await updateBook.mutateAsync(request)
    notify(`"${saved.title}" was saved.`)
    await navigate('/books')
  }

  return <BookForm initialValues={toFormValues(book)} submitLabel="Save changes" onSubmit={submit} />
}
