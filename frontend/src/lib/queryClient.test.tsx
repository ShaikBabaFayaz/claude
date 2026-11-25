import { QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http as mswHttp, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { ApiError, http } from '../api/http'
import { server } from '../test/server'
import { createTestQueryClient } from '../test/render'
import { createQueryClient, retryPolicy } from './queryClient'

const unavailable = new ApiError({ status: 502, code: 'BACKEND_UNAVAILABLE', message: 'down' })
const serverError = new ApiError({ status: 500, code: 'INTERNAL_ERROR', message: 'boom' })
const notFound = new ApiError({ status: 404, code: 'BOOK_NOT_FOUND', message: 'gone' })
const conflict = new ApiError({ status: 409, code: 'NO_COPY_AVAILABLE', message: 'none' })

describe('retryPolicy (DR-19, ADR-31)', () => {
  it('retries BACKEND_UNAVAILABLE once', () => {
    expect(retryPolicy(0, unavailable)).toBe(true)
    expect(retryPolicy(1, unavailable)).toBe(false)
  })

  it('retries a 5xx once', () => {
    expect(retryPolicy(0, serverError)).toBe(true)
    expect(retryPolicy(1, serverError)).toBe(false)
  })

  it('never retries a 4xx', () => {
    expect(retryPolicy(0, notFound)).toBe(false)
    expect(retryPolicy(0, conflict)).toBe(false)
    expect(retryPolicy(0, new ApiError({ status: 400, code: 'VALIDATION_FAILED', message: 'x' }))).toBe(false)
  })

  it('never retries an error that is not an ApiError', () => {
    expect(retryPolicy(0, new TypeError('render bug'))).toBe(false)
    expect(retryPolicy(0, 'string')).toBe(false)
  })
})

function wrapperFor(client: ReturnType<typeof createQueryClient>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}

describe('application query client (DR-19) against MSW', () => {
  it('retries a 500 once, then reports the error', async () => {
    let calls = 0
    server.use(
      mswHttp.get('/api/books', () => {
        calls += 1
        return new HttpResponse('Internal Server Error', { status: 500 })
      }),
    )
    const client = createQueryClient({ retryDelay: 0 })
    const { result } = renderHook(() => useQuery({ queryKey: ['books'], queryFn: () => http.get('/books') }), {
      wrapper: wrapperFor(client),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(calls).toBe(2)
    expect((result.current.error as ApiError).code).toBe('BACKEND_UNAVAILABLE')
  })

  it('recovers when the retry succeeds', async () => {
    let calls = 0
    server.use(
      mswHttp.get('/api/books', () => {
        calls += 1
        return calls === 1 ? new HttpResponse(null, { status: 502 }) : HttpResponse.json({ ok: true })
      }),
    )
    const client = createQueryClient({ retryDelay: 0 })
    const { result } = renderHook(() => useQuery({ queryKey: ['books'], queryFn: () => http.get('/books') }), {
      wrapper: wrapperFor(client),
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(calls).toBe(2)
  })

  it('does not retry a 404 (error shown immediately)', async () => {
    let calls = 0
    server.use(
      mswHttp.get('/api/books/9', () => {
        calls += 1
        return HttpResponse.json({ status: 404, code: 'BOOK_NOT_FOUND', message: 'No such book.' }, { status: 404 })
      }),
    )
    const client = createQueryClient({ retryDelay: 0 })
    const { result } = renderHook(() => useQuery({ queryKey: ['book', 9], queryFn: () => http.get('/books/9') }), {
      wrapper: wrapperFor(client),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(calls).toBe(1)
    expect((result.current.error as ApiError).code).toBe('BOOK_NOT_FOUND')
  })

  it('never retries a mutation, even for a 500', async () => {
    let calls = 0
    server.use(
      mswHttp.post('/api/loans', () => {
        calls += 1
        return new HttpResponse('Internal Server Error', { status: 500 })
      }),
    )
    const client = createQueryClient({ retryDelay: 0 })
    const { result } = renderHook(() => useMutation({ mutationFn: () => http.post('/loans', { body: {} }) }), {
      wrapper: wrapperFor(client),
    })
    result.current.mutate()
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(calls).toBe(1)
  })
})

describe('test query client helper (src/test/render.tsx)', () => {
  it('defaults to no retries so ordinary tests fail fast', async () => {
    let calls = 0
    server.use(
      mswHttp.get('/api/books', () => {
        calls += 1
        return new HttpResponse(null, { status: 500 })
      }),
    )
    const client = createTestQueryClient()
    const { result } = renderHook(() => useQuery({ queryKey: ['books'], queryFn: () => http.get('/books') }), {
      wrapper: wrapperFor(client),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(calls).toBe(1)
  })

  it('can opt in to the application retry policy', async () => {
    let calls = 0
    server.use(
      mswHttp.get('/api/books', () => {
        calls += 1
        return new HttpResponse(null, { status: 500 })
      }),
    )
    const client = createTestQueryClient({ appRetryPolicy: true })
    const { result } = renderHook(() => useQuery({ queryKey: ['books'], queryFn: () => http.get('/books') }), {
      wrapper: wrapperFor(client),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(calls).toBe(2)
  })
})
