import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { pickFieldErrors } from './fieldErrors'

const FIELDS = ['title', 'author', 'isbn'] as const

function apiError(fieldErrors: Array<{ field: string; message: string }>) {
  return new ApiError({ status: 400, code: 'VALIDATION_FAILED', message: 'Invalid.', fieldErrors })
}

describe('pickFieldErrors (AC-1.FE.3, AC-3.FE.2)', () => {
  it('returns the entries for known fields in page order, not server order', () => {
    const error = apiError([
      { field: 'isbn', message: 'Bad ISBN.' },
      { field: 'title', message: 'Blank.' },
    ])
    expect(pickFieldErrors(error, FIELDS)).toEqual([
      { field: 'title', message: 'Blank.' },
      { field: 'isbn', message: 'Bad ISBN.' },
    ])
  })

  it('leaves out fields the form does not have', () => {
    expect(pickFieldErrors(apiError([{ field: 'page', message: 'x' }]), FIELDS)).toEqual([])
  })

  it('returns nothing for an error without field errors or one that is not an ApiError', () => {
    expect(pickFieldErrors(apiError([]), FIELDS)).toEqual([])
    expect(pickFieldErrors(new Error('boom'), FIELDS)).toEqual([])
    expect(pickFieldErrors('text', FIELDS)).toEqual([])
  })
})
