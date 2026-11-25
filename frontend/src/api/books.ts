import { http } from './http'
import type { BookFilters, BookRequest, BookResponse, PageResponse } from './types'

export function listBooks(filters: BookFilters = {}, signal?: AbortSignal) {
  return http.get<PageResponse<BookResponse>>('/books', { query: filters, signal })
}

export function getBook(id: number, signal?: AbortSignal) {
  return http.get<BookResponse>(`/books/${id}`, { signal })
}

export function createBook(book: BookRequest) {
  return http.post<BookResponse>('/books', { body: book })
}

export function updateBook(id: number, book: BookRequest) {
  return http.put<BookResponse>(`/books/${id}`, { body: book })
}

export function deleteBook(id: number) {
  return http.delete<void>(`/books/${id}`)
}
