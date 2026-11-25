import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '../api/http'

/** Number of automatic retries a failed query gets (DR-19). */
export const MAX_QUERY_RETRIES = 1

/**
 * Query retry policy (architecture 9.3, DR-19, ADR-31): one automatic retry, and only when
 * the backend is unreachable or answered with a 5xx. A 4xx (for example a 404 on the edit page
 * of a deleted book) is shown immediately; unknown non-API errors are never retried.
 */
export function retryPolicy(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_QUERY_RETRIES) return false
  if (!isApiError(error)) return false
  return error.isBackendUnavailable || error.status >= 500
}

export interface QueryClientOptions {
  /** Override the retry delay, mainly so tests do not wait for the default back-off. */
  retryDelay?: number
}

/** Mutations are never retried automatically: checkout is not idempotent. */
export function createQueryClient({ retryDelay }: QueryClientOptions = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: retryPolicy, ...(retryDelay === undefined ? {} : { retryDelay }) },
      mutations: { retry: false },
    },
  })
}
