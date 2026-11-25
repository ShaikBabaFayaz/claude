import {
  BACKEND_UNAVAILABLE,
  type ApiErrorBody,
  type FieldErrorDto,
} from './types'

/** All API calls go through the Vite proxy on the same origin (architecture D7). */
export const API_BASE = '/api'

/** CSRF-style guard header required by the backend on every non-safe method (DR-17). */
export const REQUIRED_HEADER_NAME = 'X-Requested-With'
export const REQUIRED_HEADER_VALUE = 'library-ui'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'

const SAFE_METHODS: ReadonlySet<string> = new Set(['GET', 'HEAD', 'OPTIONS'])

/**
 * Error thrown by every API call. `code` is a backend code (architecture 6.2) or
 * `BACKEND_UNAVAILABLE` when the server could not be reached or replied with anything
 * other than a valid ApiError JSON body (DR-10).
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldErrorDto[]

  constructor(init: {
    status: number
    code: string
    message: string
    fieldErrors?: FieldErrorDto[]
    cause?: unknown
  }) {
    super(init.message, init.cause === undefined ? undefined : { cause: init.cause })
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.fieldErrors = init.fieldErrors ?? []
  }

  /** True when the server could not be reached or gave an unusable answer. */
  get isBackendUnavailable(): boolean {
    return this.code === BACKEND_UNAVAILABLE
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

const UNAVAILABLE_MESSAGE =
  'The library server cannot be reached. Check that it is running, then try again.'

function backendUnavailable(status: number, cause?: unknown): ApiError {
  return new ApiError({ status, code: BACKEND_UNAVAILABLE, message: UNAVAILABLE_MESSAGE, cause })
}

function isAbortError(error: unknown): boolean {
  // Duck-typed: the DOMException of fetch may come from a different realm than the page's global.
  return typeof error === 'object' && error !== null && (error as { name?: unknown }).name === 'AbortError'
}

function isFieldError(value: unknown): value is FieldErrorDto {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.field === 'string' && typeof candidate.message === 'string'
}

/** Accepts only JSON with `status`, `code` and `message` (architecture 9.3). */
export function parseApiErrorBody(text: string): ApiErrorBody | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return null
  const candidate = parsed as Record<string, unknown>
  if (
    typeof candidate.status !== 'number' ||
    typeof candidate.code !== 'string' ||
    typeof candidate.message !== 'string'
  ) {
    return null
  }
  return {
    status: candidate.status,
    code: candidate.code,
    message: candidate.message,
    timestamp: typeof candidate.timestamp === 'string' ? candidate.timestamp : undefined,
    fieldErrors: Array.isArray(candidate.fieldErrors) ? candidate.fieldErrors.filter(isFieldError) : [],
  }
}

export type QueryValue = string | number | boolean | null | undefined
export type QueryParams = object

/** Builds `?a=1&b=2`; undefined, null and empty-string values are omitted. Returns '' when nothing is left. */
export function buildQueryString(params?: QueryParams): string {
  if (!params) return ''
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params as Record<string, QueryValue>)) {
    if (value === undefined || value === null || value === '') continue
    search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export interface RequestOptions {
  query?: QueryParams
  body?: unknown
  signal?: AbortSignal
}

/**
 * Sends a JSON request and returns the parsed body (`undefined` for an empty body such as 204).
 * Failures always surface as `ApiError`; an aborted request rethrows the `AbortError` untouched
 * so TanStack Query can cancel quietly.
 */
export async function request<T>(
  method: HttpMethod,
  path: string,
  { query, body, signal }: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (!SAFE_METHODS.has(method)) headers[REQUIRED_HEADER_NAME] = REQUIRED_HEADER_VALUE
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  let text: string
  try {
    response = await fetch(`${API_BASE}${path}${buildQueryString(query)}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
    // The body is read as text so a proxy's empty or plain-text error never reaches a JSON parser (DR-10).
    text = await response.text()
  } catch (error) {
    if (isAbortError(error)) throw error
    throw backendUnavailable(0, error)
  }

  if (!response.ok) {
    const errorBody = parseApiErrorBody(text)
    if (!errorBody) throw backendUnavailable(response.status)
    throw new ApiError({
      status: response.status,
      code: errorBody.code,
      message: errorBody.message,
      fieldErrors: errorBody.fieldErrors,
    })
  }

  if (text.trim() === '') return undefined as T
  try {
    return JSON.parse(text) as T
  } catch (error) {
    // A 2xx that is not JSON (for example an HTML page served instead of the API) is unusable.
    throw backendUnavailable(response.status, error)
  }
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'body'>) => request<T>('GET', path, options),
  post: <T>(path: string, options?: RequestOptions) => request<T>('POST', path, options),
  put: <T>(path: string, options?: RequestOptions) => request<T>('PUT', path, options),
  delete: <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, options),
}
