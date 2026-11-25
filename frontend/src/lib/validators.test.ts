import { describe, expect, it } from 'vitest'
import { loadVectors } from '../test/vectors'
import {
  isValidEmail,
  isValidIsbn,
  normalizeIsbn,
  validateAuthor,
  validateEmail,
  validateIsbn,
  validateMemberName,
  validateTitle,
  validateTotalCopies,
} from './validators'

const isbnVectors = loadVectors('isbn')
const emailVectors = loadVectors('email')

describe('ISBN validator against the shared vectors (FR-5, AC-1.FE.2, R-4)', () => {
  it('has vectors to run (guards against an empty or misread file)', () => {
    expect(isbnVectors.valid.length).toBeGreaterThan(5)
    expect(isbnVectors.invalid.length).toBeGreaterThan(5)
  })

  it.each(isbnVectors.valid.map((v) => [JSON.stringify(v.input), v] as const))(
    'accepts valid vector %s and normalises it',
    (_label, vector) => {
      expect(isValidIsbn(vector.input)).toBe(true)
      expect(validateIsbn(vector.input)).toBeUndefined()
      if (vector.normalized !== undefined) {
        expect(normalizeIsbn(vector.input.trim())).toBe(vector.normalized)
        expect(normalizeIsbn(vector.input)).toBe(vector.normalized)
      }
    },
  )

  it.each(isbnVectors.invalid.map((v) => [JSON.stringify(v.input), v] as const))(
    'rejects invalid vector %s',
    (_label, vector) => {
      expect(isValidIsbn(vector.input)).toBe(false)
      expect(validateIsbn(vector.input)).toBeTypeOf('string')
    },
  )

  it('never converts ISBN-10 to ISBN-13 (Q-A1, ADR-22)', () => {
    for (const pair of isbnVectors.notEquivalent ?? []) {
      expect(isValidIsbn(pair.a)).toBe(true)
      expect(isValidIsbn(pair.b)).toBe(true)
      expect(normalizeIsbn(pair.a)).not.toBe(normalizeIsbn(pair.b))
    }
    expect(isbnVectors.notEquivalent?.length ?? 0).toBeGreaterThan(0)
  })
})

describe('email validator against the shared vectors (FR-11, AC-3.BE.2, R-4)', () => {
  it('has vectors to run', () => {
    expect(emailVectors.valid.length).toBeGreaterThan(3)
    expect(emailVectors.invalid.length).toBeGreaterThan(5)
  })

  it.each(emailVectors.valid.map((v) => [JSON.stringify(v.input.slice(0, 60)), v] as const))(
    'accepts valid vector %s',
    (_label, vector) => {
      expect(isValidEmail(vector.input)).toBe(true)
      expect(validateEmail(vector.input)).toBeUndefined()
    },
  )

  it.each(emailVectors.invalid.map((v) => [JSON.stringify(v.input.slice(0, 60)), v] as const))(
    'rejects invalid vector %s',
    (_label, vector) => {
      expect(isValidEmail(vector.input)).toBe(false)
      expect(validateEmail(vector.input)).toBeTypeOf('string')
    },
  )
})

describe('normalizeIsbn', () => {
  it('removes hyphens and spaces and upper-cases a trailing x only', () => {
    expect(normalizeIsbn('0-8044-2957-x')).toBe('080442957X')
    expect(normalizeIsbn(' 978 0 306 40615 7 ')).toBe('9780306406157')
    expect(normalizeIsbn('x1')).toBe('x1')
  })
})

describe('form field validators (AC-1.FE.2, AC-3.FE.2)', () => {
  it('requires a title and author and caps them at 255 characters after trimming', () => {
    expect(validateTitle('')).toBe('Title is required.')
    expect(validateTitle('   ')).toBe('Title is required.')
    expect(validateTitle(' ' + 'a'.repeat(255) + ' ')).toBeUndefined()
    expect(validateTitle('a'.repeat(256))).toBe('Title must be at most 255 characters.')
    expect(validateAuthor('')).toBe('Author is required.')
    expect(validateAuthor('a'.repeat(256))).toBe('Author must be at most 255 characters.')
  })

  it('requires a member name of at most 100 characters', () => {
    expect(validateMemberName('')).toBe('Name is required.')
    expect(validateMemberName('a'.repeat(100))).toBeUndefined()
    expect(validateMemberName('a'.repeat(101))).toBe('Name must be at most 100 characters.')
  })

  it('accepts only whole numbers 1..999 for total copies', () => {
    expect(validateTotalCopies('1')).toBeUndefined()
    expect(validateTotalCopies('999')).toBeUndefined()
    expect(validateTotalCopies(5)).toBeUndefined()
    expect(validateTotalCopies('')).toBe('Total copies is required.')
    for (const bad of ['0', '1000', '2.5', '-1', 'abc', '1e2', '5 5', 0, 1000, 2.5]) {
      expect(validateTotalCopies(bad), `value ${String(bad)}`).toBe(
        'Total copies must be a whole number between 1 and 999.',
      )
    }
  })

  it('reports required vs invalid ISBN and email separately', () => {
    expect(validateIsbn('')).toBe('ISBN is required.')
    expect(validateIsbn('123')).toBe('ISBN must be a valid ISBN-10 or ISBN-13.')
    expect(validateEmail('')).toBe('Email is required.')
    expect(validateEmail('nope')).toMatch(/valid email/)
    expect(validateEmail(`${'a'.repeat(250)}@b.co`)).toMatch(/at most 254/)
    expect(validateEmail('  ada@example.org  ')).toBeUndefined()
  })
})
