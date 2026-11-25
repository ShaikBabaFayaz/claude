const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * Parses an API date (`yyyy-MM-dd`) as a local calendar date. `new Date('2026-11-25')` would
 * be read as UTC midnight and show the previous day west of Greenwich (NFR-12, architecture 9.3).
 */
export function parseLocalDate(value: string): Date {
  const match = ISO_DATE.exec(value)
  if (!match) throw new RangeError(`Not a yyyy-MM-dd date: "${value}"`)
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day)
  // new Date(2026, 1, 31) silently rolls over to March; reject impossible dates instead.
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    throw new RangeError(`Not a real calendar date: "${value}"`)
  }
  return date
}

/** Formats an API date for display in the browser locale, e.g. "Dec 9, 2026". */
export function formatDate(value: string, locale?: string | string[]): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(parseLocalDate(value))
}
