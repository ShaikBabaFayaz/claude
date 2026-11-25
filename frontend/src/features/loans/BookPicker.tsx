import { useState } from 'react'
import type { BookResponse } from '../../api/types'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { AvailabilityBadge } from '../books/AvailabilityBadge'
import { BOOK_FILTER_MAX_LENGTH, useBooksList } from '../books/useBookQueries'
import { SearchPicker } from './SearchPicker'

interface BookPickerProps {
  selected: BookResponse | null
  onSelect: (book: BookResponse) => void
}

/** Books searched by title, each with an availability badge (AC-4.FE.1, FR-9). */
export function BookPicker({ selected, onSelect }: BookPickerProps) {
  const [search, setSearch] = useState('')
  const debounced = useDebouncedValue(search.trim())
  const query = useBooksList({ title: debounced || undefined })

  return (
    <SearchPicker
      searchLabel="Find a book by title"
      legend="Matching books"
      noun={{ singular: 'book', plural: 'books' }}
      search={search}
      onSearchChange={setSearch}
      maxSearchLength={BOOK_FILTER_MAX_LENGTH}
      query={query}
      selected={selected}
      onSelect={onSelect}
      renderItem={(book) => (
        <>
          <span>{book.title}</span>
          <span>{book.author}</span>
          <AvailabilityBadge availableCopies={book.availableCopies} />
        </>
      )}
      selectedText={(book) => book.title}
      emptyText="No book matches your search."
    />
  )
}
