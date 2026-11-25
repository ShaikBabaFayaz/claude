import { isApiError } from '../api/http'
import { ERROR_CODES, type ErrorCode } from '../api/types'

const GENERIC_MESSAGE = 'Something went wrong. Please try again.'

/**
 * Plain-language text for every error code (architecture 6.2 plus the client-only
 * `BACKEND_UNAVAILABLE`). Typed as a full Record so a new code cannot be added without a message.
 */
export const ERROR_MESSAGES: Record<ErrorCode, string> = {
  VALIDATION_FAILED: 'Some of the entered values are not valid. Check the marked fields and try again.',
  TOTAL_COPIES_BELOW_ACTIVE_LOANS:
    'Total copies cannot be lower than the number of copies currently on loan.',
  MALFORMED_REQUEST: 'The request could not be understood. Reload the page and try again.',
  MISSING_REQUIRED_HEADER: 'The request was blocked as a safety measure. Reload the page and try again.',
  FORBIDDEN_ORIGIN:
    'The server refused this request because of the address it came from. Open the application at http://localhost:5173.',
  BOOK_NOT_FOUND: 'This book no longer exists. It may have been deleted.',
  MEMBER_NOT_FOUND: 'This member no longer exists.',
  LOAN_NOT_FOUND: 'This loan could not be found.',
  NOT_FOUND: 'The requested item could not be found.',
  METHOD_NOT_ALLOWED: 'This action is not supported by the server.',
  UNSUPPORTED_MEDIA_TYPE: 'The server could not read the data that was sent.',
  REQUEST_REJECTED: 'The server rejected this request.',
  ISBN_ALREADY_EXISTS: 'A book with this ISBN already exists.',
  EMAIL_ALREADY_EXISTS: 'A member with this email address already exists.',
  BOOK_HAS_ACTIVE_LOANS: 'This book cannot be deleted while copies are on loan. Return them first.',
  MEMBER_HAS_ACTIVE_LOANS:
    'This member cannot be deactivated while they have books on loan. Return the books first.',
  LOAN_LIMIT_REACHED: 'This member already has 3 books on loan, the maximum.',
  NO_COPY_AVAILABLE: 'No copy of this book is available right now.',
  MEMBER_INACTIVE: 'This member is inactive and cannot borrow books. Reactivate the member first.',
  DUPLICATE_ACTIVE_LOAN: 'This member already has this book on loan.',
  LOAN_ALREADY_RETURNED: 'This loan has already been returned.',
  CONCURRENT_UPDATE: 'Someone else changed this at the same time. Please try again.',
  INTERNAL_ERROR: 'The server ran into an unexpected problem. Please try again.',
  BACKEND_UNAVAILABLE: 'The library server cannot be reached. Check that it is running, then try again.',
}

function isKnownCode(code: string): code is ErrorCode {
  return (ERROR_CODES as readonly string[]).includes(code)
}

/**
 * Message for an error code. Unknown codes (a newer backend) fall back to the server's own
 * message when given, then to a generic sentence.
 */
export function messageForCode(code: string, serverMessage?: string): string {
  if (isKnownCode(code)) return ERROR_MESSAGES[code]
  return serverMessage && serverMessage.trim() !== '' ? serverMessage : GENERIC_MESSAGE
}

/** Friendly text for any thrown value; never exposes raw exception text. */
export function getErrorMessage(error: unknown): string {
  if (isApiError(error)) return messageForCode(error.code, error.message)
  return GENERIC_MESSAGE
}
