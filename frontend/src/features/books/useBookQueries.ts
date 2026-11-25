import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createBook, deleteBook, getBook, listBooks, updateBook } from '../../api/books'
import { queryKeys } from '../../api/queryKeys'
import type { BookAvailabilityFilter, BookFilters, BookRequest } from '../../api/types'

/** Longest text filter the API accepts (DR-18); longer values would be answered with a 400. */
export const BOOK_FILTER_MAX_LENGTH = 255

const AVAILABILITY_VALUES: readonly BookAvailabilityFilter[] = ['available', 'unavailable']

function readText(params: URLSearchParams, name: string): string | undefined {
  const value = params.get(name)?.trim().slice(0, BOOK_FILTER_MAX_LENGTH)
  return value ? value : undefined
}

/**
 * Reads the Books screen state from the URL query string (architecture 9.3): `title`, `author`,
 * `isbn`, `availability` and `page` (zero-based). Anything invalid is dropped rather than sent to
 * the API, so a hand-edited URL cannot produce a 400. Reloading or pressing Back therefore
 * restores the same view.
 */
export function parseBookFilters(params: URLSearchParams): BookFilters {
  const availability = params.get('availability')
  const page = Number(params.get('page'))
  return {
    title: readText(params, 'title'),
    author: readText(params, 'author'),
    isbn: readText(params, 'isbn'),
    availability: AVAILABILITY_VALUES.find((value) => value === availability),
    page: Number.isInteger(page) && page > 0 ? page : undefined,
  }
}

/** Inverse of `parseBookFilters`; empty values and page 0 are left out to keep URLs short. */
export function bookFiltersToSearchParams(filters: BookFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.title) params.set('title', filters.title)
  if (filters.author) params.set('author', filters.author)
  if (filters.isbn) params.set('isbn', filters.isbn)
  if (filters.availability) params.set('availability', filters.availability)
  if (filters.page) params.set('page', String(filters.page))
  return params
}

/** True when any filter (not the page) narrows the list: tells "no books yet" from "no match" (AC-2.FE.5). */
export function hasActiveBookFilters(filters: BookFilters): boolean {
  return Boolean(filters.title || filters.author || filters.isbn || filters.availability)
}

/** A page of books for the given filters. The previous page stays visible while the next one loads. */
export function useBooksList(filters: BookFilters) {
  return useQuery({
    queryKey: queryKeys.books(filters),
    queryFn: ({ signal }) => listBooks(filters, signal),
    placeholderData: keepPreviousData,
  })
}

export function useBook(id: number) {
  return useQuery({
    queryKey: queryKeys.book(id),
    queryFn: ({ signal }) => getBook(id, signal),
  })
}

/** Create, update and delete refresh every cached book list and detail (architecture 9.3). */
export function useCreateBook() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (book: BookRequest) => createBook(book),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.booksRoot })
    },
  })
}

export function useUpdateBook(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (book: BookRequest) => updateBook(id, book),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.booksRoot })
    },
  })
}

/**
 * Delete waits for the refreshed list before the mutation counts as finished, so the confirmation
 * dialog closes only once the deleted row is gone and focus can move to the notice (DR-11). A failed
 * delete (for example 409) also refreshes the list so a stale row is corrected.
 */
export function useDeleteBook() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.booksRoot })
  return useMutation({
    mutationFn: (id: number) => deleteBook(id),
    onSuccess: refresh,
    onError: refresh,
  })
}
