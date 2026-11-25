import { delay, http as mswHttp, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { ApiError, buildQueryString, http, parseApiErrorBody, request } from './http'

const validationBody = {
  status: 400,
  code: 'VALIDATION_FAILED',
  message: 'The request contains invalid fields.',
  timestamp: '2026-11-25T10:15:30.123+01:00',
  fieldErrors: [{ field: 'isbn', message: 'ISBN must be a valid ISBN-10 or ISBN-13.' }],
}

async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError)
    return error as ApiError
  }
  throw new Error('Expected the request to fail')
}

describe('http.ts failure mapping (AC-X.3, DR-10, ADR-31)', () => {
  it('maps a 500 with a plain-text body to BACKEND_UNAVAILABLE', async () => {
    server.use(mswHttp.get('/api/books', () => new HttpResponse('Internal Server Error', { status: 500 })))
    const error = await failureOf(http.get('/books'))
    expect(error.code).toBe('BACKEND_UNAVAILABLE')
    expect(error.isBackendUnavailable).toBe(true)
    expect(error.status).toBe(500)
    expect(error.message).not.toMatch(/Internal Server Error/)
  })

  it('maps a 502 with an empty body to BACKEND_UNAVAILABLE', async () => {
    server.use(mswHttp.get('/api/books', () => new HttpResponse(null, { status: 502 })))
    const error = await failureOf(http.get('/books'))
    expect(error.code).toBe('BACKEND_UNAVAILABLE')
    expect(error.status).toBe(502)
  })

  it('maps a network error (rejected fetch) to BACKEND_UNAVAILABLE', async () => {
    server.use(mswHttp.get('/api/books', () => HttpResponse.error()))
    const error = await failureOf(http.get('/books'))
    expect(error.code).toBe('BACKEND_UNAVAILABLE')
    expect(error.status).toBe(0)
  })

  it('maps non-ApiError JSON on an error status to BACKEND_UNAVAILABLE', async () => {
    server.use(mswHttp.get('/api/books', () => HttpResponse.json({ error: 'Bad Gateway' }, { status: 502 })))
    expect((await failureOf(http.get('/books'))).code).toBe('BACKEND_UNAVAILABLE')
  })

  it('maps an HTML error page (for example the SPA fallback) to BACKEND_UNAVAILABLE', async () => {
    server.use(
      mswHttp.get(
        '/api/books',
        () => new HttpResponse('<html><body>Not Found</body></html>', { status: 404, headers: { 'Content-Type': 'text/html' } }),
      ),
    )
    expect((await failureOf(http.get('/books'))).code).toBe('BACKEND_UNAVAILABLE')
  })

  it('maps a 2xx response that is not JSON to BACKEND_UNAVAILABLE', async () => {
    server.use(mswHttp.get('/api/books', () => new HttpResponse('<html></html>', { status: 200 })))
    expect((await failureOf(http.get('/books'))).code).toBe('BACKEND_UNAVAILABLE')
  })

  it('keeps code, message and fieldErrors of a valid 400 ApiError body', async () => {
    server.use(mswHttp.post('/api/books', () => HttpResponse.json(validationBody, { status: 400 })))
    const error = await failureOf(http.post('/books', { body: { isbn: 'x' } }))
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.status).toBe(400)
    expect(error.message).toBe('The request contains invalid fields.')
    expect(error.fieldErrors).toEqual([{ field: 'isbn', message: 'ISBN must be a valid ISBN-10 or ISBN-13.' }])
    expect(error.isBackendUnavailable).toBe(false)
  })

  it('keeps a business-rule 409 and defaults fieldErrors to an empty list', async () => {
    server.use(
      mswHttp.post('/api/loans', () =>
        HttpResponse.json(
          { status: 409, code: 'NO_COPY_AVAILABLE', message: 'No copy available.', timestamp: 'x' },
          { status: 409 },
        ),
      ),
    )
    const error = await failureOf(http.post('/loans', { body: {} }))
    expect(error.code).toBe('NO_COPY_AVAILABLE')
    expect(error.fieldErrors).toEqual([])
  })

  it('keeps a 500 that carries a valid ApiError body as that error (not "unavailable")', async () => {
    server.use(
      mswHttp.get('/api/books', () =>
        HttpResponse.json({ status: 500, code: 'INTERNAL_ERROR', message: 'Unexpected.' }, { status: 500 }),
      ),
    )
    const error = await failureOf(http.get('/books'))
    expect(error.code).toBe('INTERNAL_ERROR')
    expect(error.status).toBe(500)
  })

  it('rethrows an abort untouched so cancelled queries are not shown as errors', async () => {
    server.use(
      mswHttp.get('/api/books', async () => {
        await delay(1000)
        return HttpResponse.json({})
      }),
    )
    const controller = new AbortController()
    const pending = http.get('/books', { signal: controller.signal })
    controller.abort()
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
    await expect(pending).rejects.not.toBeInstanceOf(ApiError)
  })

  it('treats an abort raised by fetch from another realm as an abort too (duck-typed)', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockRejectedValue({ name: 'AbortError' })
    try {
      await expect(http.get('/books')).rejects.toMatchObject({ name: 'AbortError' })
    } finally {
      fetchSpy.mockRestore()
    }
  })
})

describe('http.ts request headers and bodies (AC-X.3, DR-17, D7)', () => {
  it('sends X-Requested-With: library-ui on POST, PUT and DELETE', async () => {
    const seen: Record<string, string | null> = {}
    server.use(
      mswHttp.post('/api/m', ({ request: r }) => {
        seen.POST = r.headers.get('X-Requested-With')
        return HttpResponse.json({})
      }),
      mswHttp.put('/api/m', ({ request: r }) => {
        seen.PUT = r.headers.get('X-Requested-With')
        return HttpResponse.json({})
      }),
      mswHttp.delete('/api/m', ({ request: r }) => {
        seen.DELETE = r.headers.get('X-Requested-With')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await http.post('/m')
    await http.put('/m', { body: {} })
    await http.delete('/m')
    expect(seen).toEqual({ POST: 'library-ui', PUT: 'library-ui', DELETE: 'library-ui' })
  })

  it('sends the header on PATCH too (every method except GET, HEAD, OPTIONS)', async () => {
    let header: string | null = null
    server.use(
      mswHttp.patch('/api/m', ({ request: r }) => {
        header = r.headers.get('X-Requested-With')
        return HttpResponse.json({})
      }),
    )
    await request('PATCH', '/m', { body: {} })
    expect(header).toBe('library-ui')
  })

  it('does not send the header on GET', async () => {
    let header: string | null = 'unset'
    server.use(
      mswHttp.get('/api/m', ({ request: r }) => {
        header = r.headers.get('X-Requested-With')
        return HttpResponse.json({})
      }),
    )
    await http.get('/m')
    expect(header).toBeNull()
  })

  it('sends a JSON body with a JSON content type, and no content type without a body', async () => {
    let withBody: { type: string | null; json: unknown } | undefined
    let withoutBody: string | null = 'unset'
    server.use(
      mswHttp.post('/api/with', async ({ request: r }) => {
        withBody = { type: r.headers.get('Content-Type'), json: await r.json() }
        return HttpResponse.json({})
      }),
      mswHttp.post('/api/without', ({ request: r }) => {
        withoutBody = r.headers.get('Content-Type')
        return HttpResponse.json({})
      }),
    )
    await http.post('/with', { body: { title: 'A' } })
    await http.post('/without')
    expect(withBody).toEqual({ type: 'application/json', json: { title: 'A' } })
    expect(withoutBody).toBeNull()
  })

  it('returns the parsed body, and undefined for 204', async () => {
    server.use(
      mswHttp.get('/api/one', () => HttpResponse.json({ id: 1 })),
      mswHttp.delete('/api/one', () => new HttpResponse(null, { status: 204 })),
    )
    await expect(http.get('/one')).resolves.toEqual({ id: 1 })
    await expect(http.delete('/one')).resolves.toBeUndefined()
  })

  it('appends the query string, omitting empty values', async () => {
    let url = ''
    server.use(
      mswHttp.get('/api/books', ({ request: r }) => {
        url = r.url
        return HttpResponse.json({})
      }),
    )
    await http.get('/books', { query: { title: 'clean code', author: '', isbn: undefined, page: 0, size: 20 } })
    expect(new URL(url).search).toBe('?title=clean+code&page=0&size=20')
  })
})

describe('buildQueryString', () => {
  it('returns an empty string when nothing is set', () => {
    expect(buildQueryString()).toBe('')
    expect(buildQueryString({})).toBe('')
    expect(buildQueryString({ a: undefined, b: null, c: '' })).toBe('')
  })

  it('keeps zero and false, and encodes special characters', () => {
    expect(buildQueryString({ page: 0, flag: false })).toBe('?page=0&flag=false')
    expect(buildQueryString({ search: 'a&b=c 100%' })).toBe('?search=a%26b%3Dc+100%25')
  })
})

describe('parseApiErrorBody', () => {
  it('accepts a body with status, code and message', () => {
    expect(parseApiErrorBody(JSON.stringify(validationBody))?.code).toBe('VALIDATION_FAILED')
  })

  it.each([
    ['empty text', ''],
    ['plain text', 'Bad gateway'],
    ['a JSON array', '[]'],
    ['JSON null', 'null'],
    ['missing code', '{"status":400,"message":"x"}'],
    ['non-numeric status', '{"status":"400","code":"X","message":"x"}'],
    ['missing message', '{"status":400,"code":"X"}'],
  ])('rejects %s', (_label, text) => {
    expect(parseApiErrorBody(text)).toBeNull()
  })

  it('drops malformed fieldErrors entries instead of failing', () => {
    const body = parseApiErrorBody(
      JSON.stringify({ ...validationBody, fieldErrors: [{ field: 'a', message: 'b' }, { field: 1 }, null] }),
    )
    expect(body?.fieldErrors).toEqual([{ field: 'a', message: 'b' }])
  })
})
