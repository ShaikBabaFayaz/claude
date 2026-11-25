/**
 * Client-side mirrors of the backend rules (FR-5, FR-11; architecture 5.3). They are checked
 * against the same `shared-test-vectors/*.json` files as the backend validators (R-4), so the
 * two sides cannot drift. The server stays authoritative; these only block obvious mistakes.
 */

export const LIMITS = {
  titleMax: 255,
  authorMax: 255,
  isbnMaxInput: 40,
  copiesMin: 1,
  copiesMax: 999,
  memberNameMax: 100,
  emailMax: 254,
} as const

/** Removes hyphens and spaces and upper-cases a trailing `x`. Never converts ISBN-10 to ISBN-13 (Q-A1). */
export function normalizeIsbn(raw: string): string {
  const stripped = raw.replace(/[- ]/g, '')
  return stripped.endsWith('x') ? `${stripped.slice(0, -1)}X` : stripped
}

function isValidIsbn10(isbn: string): boolean {
  if (!/^\d{9}[\dX]$/.test(isbn)) return false
  let sum = 0
  for (let i = 0; i < 10; i++) {
    const char = isbn[i]
    const digit = char === 'X' ? 10 : Number(char)
    sum += digit * (10 - i)
  }
  return sum % 11 === 0
}

function isValidIsbn13(isbn: string): boolean {
  if (!/^\d{13}$/.test(isbn)) return false
  let sum = 0
  for (let i = 0; i < 13; i++) {
    sum += Number(isbn[i]) * (i % 2 === 0 ? 1 : 3)
  }
  return sum % 10 === 0
}

/** True for a valid ISBN-10 or ISBN-13 (hyphens and spaces ignored, check digit verified). */
export function isValidIsbn(raw: string): boolean {
  const isbn = normalizeIsbn(raw)
  return isValidIsbn10(isbn) || isValidIsbn13(isbn)
}

/**
 * Email rule of AC-3.BE.2 (as pinned in shared-test-vectors/README.md): exactly one `@`, a
 * non-empty local part, a domain with at least one dot and no empty label, no whitespace, at most
 * 254 characters. Deliberately not a full RFC 5322 parser. Input is expected to be trimmed already.
 */
export function isValidEmail(email: string): boolean {
  if (email.length === 0 || email.length > LIMITS.emailMax) return false
  if (/\s/.test(email)) return false
  const parts = email.split('@')
  if (parts.length !== 2) return false
  const [local, domain] = parts
  if (local.length === 0) return false
  const labels = domain.split('.')
  return labels.length >= 2 && labels.every((label) => label.length > 0)
}

/* ---- Field validators for forms: return an error message, or undefined when the value is fine. ---- */

export function validateRequiredText(value: string, label: string, maxLength: number): string | undefined {
  const trimmed = value.trim()
  if (trimmed.length === 0) return `${label} is required.`
  if (trimmed.length > maxLength) return `${label} must be at most ${maxLength} characters.`
  return undefined
}

export const validateTitle = (value: string) => validateRequiredText(value, 'Title', LIMITS.titleMax)
export const validateAuthor = (value: string) => validateRequiredText(value, 'Author', LIMITS.authorMax)
export const validateMemberName = (value: string) =>
  validateRequiredText(value, 'Name', LIMITS.memberNameMax)

export function validateIsbn(value: string): string | undefined {
  if (value.trim().length === 0) return 'ISBN is required.'
  if (!isValidIsbn(value.trim())) return 'ISBN must be a valid ISBN-10 or ISBN-13.'
  return undefined
}

export function validateTotalCopies(value: string | number): string | undefined {
  const message = `Total copies must be a whole number between ${LIMITS.copiesMin} and ${LIMITS.copiesMax}.`
  const text = String(value).trim()
  if (text.length === 0) return 'Total copies is required.'
  if (!/^\d+$/.test(text)) return message
  const copies = Number(text)
  return copies >= LIMITS.copiesMin && copies <= LIMITS.copiesMax ? undefined : message
}

export function validateEmail(value: string): string | undefined {
  const trimmed = value.trim()
  if (trimmed.length === 0) return 'Email is required.'
  if (trimmed.length > LIMITS.emailMax) return `Email must be at most ${LIMITS.emailMax} characters.`
  if (!isValidEmail(trimmed)) return 'Enter a valid email address, for example name@example.org.'
  return undefined
}
