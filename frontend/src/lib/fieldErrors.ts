import { isApiError } from '../api/http'

export interface FormFieldError<F extends string> {
  field: F
  message: string
}

/**
 * The server `fieldErrors` of a failed request that belong to a field of this form, in page order
 * (the order of `fields`), so the first entry is the field to focus (architecture 9.3, AC-1.FE.3,
 * AC-3.FE.2). Entries for fields the form does not have are left out: the caller then shows the
 * error as a form-level message. Returns an empty list for errors that are not `ApiError`s.
 */
export function pickFieldErrors<F extends string>(error: unknown, fields: readonly F[]): FormFieldError<F>[] {
  if (!isApiError(error)) return []
  const picked: FormFieldError<F>[] = []
  for (const entry of error.fieldErrors) {
    const field = fields.find((name) => name === entry.field)
    if (field !== undefined) picked.push({ field, message: entry.message })
  }
  return picked.sort((a, b) => fields.indexOf(a.field) - fields.indexOf(b.field))
}
