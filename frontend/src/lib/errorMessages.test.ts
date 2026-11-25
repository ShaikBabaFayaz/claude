import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/http'
import { ERROR_CODES, SERVER_ERROR_CODES } from '../api/types'
import { ERROR_MESSAGES, getErrorMessage, messageForCode } from './errorMessages'

describe('errorMessages (FR-22, AC-4.FE.3, AC-X.3)', () => {
  it('covers exactly the codes of architecture 6.2 plus BACKEND_UNAVAILABLE', () => {
    expect([...SERVER_ERROR_CODES].sort()).toEqual(
      [
        'VALIDATION_FAILED',
        'TOTAL_COPIES_BELOW_ACTIVE_LOANS',
        'MALFORMED_REQUEST',
        'MISSING_REQUIRED_HEADER',
        'FORBIDDEN_ORIGIN',
        'BOOK_NOT_FOUND',
        'MEMBER_NOT_FOUND',
        'LOAN_NOT_FOUND',
        'NOT_FOUND',
        'METHOD_NOT_ALLOWED',
        'UNSUPPORTED_MEDIA_TYPE',
        'REQUEST_REJECTED',
        'ISBN_ALREADY_EXISTS',
        'EMAIL_ALREADY_EXISTS',
        'BOOK_HAS_ACTIVE_LOANS',
        'MEMBER_HAS_ACTIVE_LOANS',
        'LOAN_LIMIT_REACHED',
        'NO_COPY_AVAILABLE',
        'MEMBER_INACTIVE',
        'DUPLICATE_ACTIVE_LOAN',
        'LOAN_ALREADY_RETURNED',
        'CONCURRENT_UPDATE',
        'INTERNAL_ERROR',
      ].sort(),
    )
    expect(ERROR_CODES).toContain('BACKEND_UNAVAILABLE')
  })

  it.each([...ERROR_CODES])('has a plain-language message for %s', (code) => {
    const message = ERROR_MESSAGES[code]
    expect(message.trim().length).toBeGreaterThan(10)
    // Plain language: no code names or exception text.
    expect(message).not.toMatch(/[A-Z]{3,}_[A-Z_]+|Exception/)
    expect(messageForCode(code)).toBe(message)
  })

  it('uses the BACKEND_UNAVAILABLE text from architecture 9.3', () => {
    expect(messageForCode('BACKEND_UNAVAILABLE')).toBe(
      'The library server cannot be reached. Check that it is running, then try again.',
    )
  })

  it('explains the loan-limit rejection as in architecture 9.3', () => {
    expect(messageForCode('LOAN_LIMIT_REACHED')).toBe('This member already has 3 books on loan, the maximum.')
  })

  it('falls back to the server message for an unknown code, then to a generic sentence', () => {
    expect(messageForCode('SOMETHING_NEW', 'The shelf is on fire.')).toBe('The shelf is on fire.')
    expect(messageForCode('SOMETHING_NEW')).toBe('Something went wrong. Please try again.')
    expect(messageForCode('SOMETHING_NEW', '   ')).toBe('Something went wrong. Please try again.')
  })

  it('getErrorMessage maps ApiError by code and never leaks raw text of other errors', () => {
    expect(getErrorMessage(new ApiError({ status: 409, code: 'NO_COPY_AVAILABLE', message: 'server text' }))).toBe(
      ERROR_MESSAGES.NO_COPY_AVAILABLE,
    )
    expect(getErrorMessage(new ApiError({ status: 418, code: 'TEAPOT', message: 'I am a teapot' }))).toBe(
      'I am a teapot',
    )
    expect(getErrorMessage(new TypeError('Cannot read properties of undefined'))).toBe(
      'Something went wrong. Please try again.',
    )
    expect(getErrorMessage('boom')).toBe('Something went wrong. Please try again.')
  })
})
