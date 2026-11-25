import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type Ref } from 'react'
import type { BookAvailabilityFilter, BookFilters as BookFilterValues } from '../../api/types'
import { FormField } from '../../components/FormField'
import { DEBOUNCE_MS } from '../../lib/useDebouncedValue'
import styles from './BookFilters.module.css'
import { BOOK_FILTER_MAX_LENGTH } from './useBookQueries'

/** What is typed in the bar. Text is kept as typed; it is trimmed when it becomes a filter. */
interface Draft {
  title: string
  author: string
  isbn: string
  availability: '' | BookAvailabilityFilter
}

const toDraft = (filters: BookFilterValues): Draft => ({
  title: filters.title ?? '',
  author: filters.author ?? '',
  isbn: filters.isbn ?? '',
  availability: filters.availability ?? '',
})

const toFilters = (draft: Draft): BookFilterValues => ({
  title: draft.title.trim() || undefined,
  author: draft.author.trim() || undefined,
  isbn: draft.isbn.trim() || undefined,
  availability: draft.availability || undefined,
})

const filterKey = (filters: BookFilterValues) =>
  JSON.stringify([filters.title ?? '', filters.author ?? '', filters.isbn ?? '', filters.availability ?? ''])

interface BookFiltersProps {
  /** The filters currently applied, read from the URL by the page. */
  filters: BookFilterValues
  /** Called with the new filters (page reset is the caller's job) after a typing pause, a submit or a change of availability. */
  onChange: (next: BookFilterValues) => void
  /** Show the "Clear filters" button (the page hides it while its own "No books found" panel offers one). */
  showClear?: boolean
  onClear: () => void
  /** Lets the page move focus here after the filters were cleared. */
  titleInputRef?: Ref<HTMLInputElement>
}

/**
 * Filter bar of the Books screen (AC-2.FE.1, AC-2.FE.2): title, author, ISBN and availability.
 * Text filters apply after a ~300 ms typing pause or at once on submit; availability applies on
 * change. The inputs hold their own draft and follow the URL again whenever it changes for a reason
 * other than this bar's own apply (Back/Forward, "Clear filters", a link).
 */
export function BookFilters({ filters, onChange, showClear = true, onClear, titleInputRef }: BookFiltersProps) {
  const urlKey = filterKey(filters)
  const [draft, setDraft] = useState<Draft>(() => toDraft(filters))
  /** The key of the last filters this bar applied; a URL change to anything else came from outside. */
  const [appliedKey, setAppliedKey] = useState(urlKey)
  const [seenUrlKey, setSeenUrlKey] = useState(urlKey)

  // Adjusting state while rendering is the documented way to derive state from a changed value
  // without an effect (and without a flash of stale inputs).
  if (urlKey !== seenUrlKey) {
    setSeenUrlKey(urlKey)
    if (urlKey !== appliedKey) {
      setAppliedKey(urlKey)
      setDraft(toDraft(filters))
    }
  }

  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  // Latest values for the timer callback, which outlives the render that created it.
  const latest = useRef({ draft, urlKey, onChange })
  useEffect(() => {
    latest.current = { draft, urlKey, onChange }
  })
  useEffect(() => () => clearTimeout(timer.current), [])

  function apply(next: Draft) {
    clearTimeout(timer.current)
    const nextFilters = toFilters(next)
    setAppliedKey(filterKey(nextFilters))
    onChange(nextFilters)
  }

  /** Text: wait for a typing pause before applying (AC-2.FE.2). */
  function type(name: 'title' | 'author' | 'isbn') {
    return (event: ChangeEvent<HTMLInputElement>) => {
      const next = { ...draft, [name]: event.target.value }
      setDraft(next)
      clearTimeout(timer.current)
      timer.current = setTimeout(() => {
        const current = latest.current
        // The inputs were reset from the URL in the meantime, or already show what is applied: nothing to do.
        if (current.draft !== next || filterKey(toFilters(next)) === current.urlKey) return
        setAppliedKey(filterKey(toFilters(next)))
        current.onChange(toFilters(next))
      }, DEBOUNCE_MS)
    }
  }

  function changeAvailability(event: ChangeEvent<HTMLSelectElement>) {
    const next = { ...draft, availability: event.target.value as Draft['availability'] }
    setDraft(next)
    apply(next)
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    apply(draft)
  }

  return (
    <form role="search" aria-label="Filter books" className={styles.bar} onSubmit={submit}>
      <div className={styles.fields}>
        <FormField label="Title">
          {(control) => (
            <input
              type="text"
              className="input"
              maxLength={BOOK_FILTER_MAX_LENGTH}
              autoComplete="off"
              ref={titleInputRef}
              value={draft.title}
              onChange={type('title')}
              {...control}
            />
          )}
        </FormField>
        <FormField label="Author">
          {(control) => (
            <input
              type="text"
              className="input"
              maxLength={BOOK_FILTER_MAX_LENGTH}
              autoComplete="off"
              value={draft.author}
              onChange={type('author')}
              {...control}
            />
          )}
        </FormField>
        <FormField label="ISBN">
          {(control) => (
            <input
              type="text"
              className="input"
              maxLength={BOOK_FILTER_MAX_LENGTH}
              autoComplete="off"
              value={draft.isbn}
              onChange={type('isbn')}
              {...control}
            />
          )}
        </FormField>
        <FormField label="Availability">
          {(control) => (
            <select className="input" value={draft.availability} onChange={changeAvailability} {...control}>
              <option value="">All</option>
              <option value="available">Available</option>
              <option value="unavailable">All copies out</option>
            </select>
          )}
        </FormField>
      </div>
      <div className={styles.actions}>
        <button type="submit" className="button button--primary">
          Search
        </button>
        {showClear && (
          <button type="button" className="button" onClick={onClear}>
            Clear filters
          </button>
        )}
      </div>
    </form>
  )
}
