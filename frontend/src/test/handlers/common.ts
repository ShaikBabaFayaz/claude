import { HttpResponse } from 'msw'
import type { ApiErrorBody, FieldErrorDto, PageResponse } from '../../api/types'

/**
 * Shared building blocks of the MSW fakes. The fixtures in `books.ts` / `members.ts` are written
 * from the API contract (architecture section 6) and kept in this one folder so they can be
 * cross-checked against the real backend response shapes in one place.
 */

/** A backend error response in the one error format (architecture 6.2, AC-X.1). */
export function apiErrorResponse(
  status: number,
  code: string,
  message: string,
  fieldErrors: FieldErrorDto[] = [],
): Response {
  const body: ApiErrorBody = {
    status,
    code,
    message,
    timestamp: '2026-11-25T10:15:30.123+01:00',
    fieldErrors,
  }
  return HttpResponse.json(body, { status })
}

/** What the Vite proxy returns while the backend is down: a bare 502 without JSON (DR-10). */
export function proxyDownResponse(): Response {
  return new HttpResponse(null, { status: 502 })
}

/** The page envelope of architecture 6.1; an empty result has `totalPages` 0 (AC-2.BE.5). */
export function pageOf<T>(all: T[], page: number, size: number): PageResponse<T> {
  const start = page * size
  return {
    content: all.slice(start, start + size),
    page,
    size,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / size),
  }
}

export interface PageQuery {
  page: number
  size: number
}

/** Reads `page` (default 0) and `size` (default 20) the way the backend defaults them (6.1). */
export function readPageQuery(url: URL): PageQuery {
  const page = Number(url.searchParams.get('page') ?? '0')
  const size = Number(url.searchParams.get('size') ?? '20')
  return {
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: Number.isInteger(size) && size >= 1 && size <= 100 ? size : 20,
  }
}

/** One request seen by a fake API, for assertions such as "no request was sent" or "one request". */
export interface RecordedRequest {
  method: string
  /** Path and query string, e.g. `/api/books?title=clean`. */
  url: string
  body?: unknown
}

export function recordedUrl(request: Request): string {
  const url = new URL(request.url)
  return `${url.pathname}${url.search}`
}
