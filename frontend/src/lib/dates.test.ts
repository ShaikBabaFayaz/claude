import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { formatDate, parseLocalDate } from './dates'

// A zone west of Greenwich is where the naive date-only parse (UTC midnight) lands on the previous day.
const originalTz = process.env.TZ
beforeAll(() => {
  process.env.TZ = 'America/Los_Angeles'
})
afterAll(() => {
  if (originalTz === undefined) delete process.env.TZ
  else process.env.TZ = originalTz
})

describe('dates (NFR-12, architecture 9.3)', () => {
  it('runs in a negative-offset zone, where the naive parse shifts a day', () => {
    expect(new Date(2026, 10, 25).getTimezoneOffset()).toBeGreaterThan(0)
    expect(new Date('2026-11-25').getDate()).toBe(24)
  })

  it('parses yyyy-MM-dd as the same local calendar day', () => {
    const date = parseLocalDate('2026-11-25')
    expect([date.getFullYear(), date.getMonth() + 1, date.getDate()]).toEqual([2026, 11, 25])
    expect(date.getHours()).toBe(0)
  })

  it('formats the same calendar day it was given, not the previous one', () => {
    expect(formatDate('2026-12-09', 'en-US')).toBe('Dec 9, 2026')
    expect(formatDate('2026-01-01', 'en-GB')).toBe('1 Jan 2026')
  })

  it('rejects malformed and impossible dates', () => {
    for (const bad of ['', '2026-1-5', '25/11/2026', '2026-11-25T00:00:00Z', '2026-02-30', '2026-13-01']) {
      expect(() => parseLocalDate(bad), bad).toThrow(RangeError)
    }
  })

  it('accepts a leap day only in a leap year', () => {
    expect(parseLocalDate('2028-02-29').getDate()).toBe(29)
    expect(() => parseLocalDate('2027-02-29')).toThrow(RangeError)
  })
})
