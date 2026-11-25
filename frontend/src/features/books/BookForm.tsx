import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import type { BookRequest } from '../../api/types'
import { FormField } from '../../components/FormField'
import { getErrorMessage } from '../../lib/errorMessages'
import { pickFieldErrors } from '../../lib/fieldErrors'
import { validateAuthor, validateIsbn, validateTitle, validateTotalCopies } from '../../lib/validators'
import styles from './BookForm.module.css'

export interface BookFormValues {
  title: string
  author: string
  isbn: string
  /** Kept as text while editing so "2.5" or "abc" can be reported instead of silently dropped. */
  totalCopies: string
}

export const EMPTY_BOOK_FORM: BookFormValues = { title: '', author: '', isbn: '', totalCopies: '' }

/** Form fields in page order: the first one with an error receives focus (AC-1.FE.3). */
const FIELD_ORDER = ['title', 'author', 'isbn', 'totalCopies'] as const

const SUMMARY_MESSAGE = 'The book was not saved. Fix the fields marked below and try again.'

interface BookFormProps {
  initialValues?: BookFormValues
  submitLabel: string
  /** Sends the request. Reject with an `ApiError` to have the server's messages shown inline. */
  onSubmit: (request: BookRequest) => Promise<unknown>
}

/**
 * Add/edit book form (AC-1.FE.2, AC-1.FE.3). Client checks from `lib/validators.ts` block the
 * request and show an inline error per field. A server 400/409 with `fieldErrors` is applied to the
 * matching fields, focus moves to the first of them and the entered values stay untouched; an error
 * without a field becomes a form-level message (architecture 9.3).
 */
export function BookForm({ initialValues = EMPTY_BOOK_FORM, submitLabel, onSubmit }: BookFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<BookFormValues>({ defaultValues: initialValues })
  const [formError, setFormError] = useState<string | null>(null)

  function showServerError(error: unknown) {
    const fieldErrors = pickFieldErrors(error, FIELD_ORDER)
    if (fieldErrors.length === 0) {
      setFormError(getErrorMessage(error))
      return
    }
    for (const entry of fieldErrors) setError(entry.field, { type: 'server', message: entry.message })
    setFormError(SUMMARY_MESSAGE)
    setFocus(fieldErrors[0].field)
  }

  const submit = handleSubmit(
    async (values) => {
      setFormError(null)
      try {
        await onSubmit({
          title: values.title.trim(),
          author: values.author.trim(),
          isbn: values.isbn.trim(),
          totalCopies: Number(values.totalCopies.trim()),
        })
      } catch (error) {
        showServerError(error)
      }
    },
    () => setFormError(SUMMARY_MESSAGE),
  )

  return (
    <form noValidate onSubmit={submit} className={styles.form}>
      {formError && (
        <p role="alert" className={styles.summary}>
          <span aria-hidden="true">{'⚠'} </span>
          {formError}
        </p>
      )}
      <FormField label="Title" required error={errors.title?.message}>
        {(control) => (
          <input type="text" className="input" autoComplete="off" {...control} {...register('title', { validate: validateTitle })} />
        )}
      </FormField>
      <FormField label="Author" required error={errors.author?.message}>
        {(control) => (
          <input type="text" className="input" autoComplete="off" {...control} {...register('author', { validate: validateAuthor })} />
        )}
      </FormField>
      <FormField
        label="ISBN"
        required
        hint="ISBN-10 or ISBN-13. Hyphens and spaces are allowed."
        error={errors.isbn?.message}
      >
        {(control) => (
          <input type="text" className="input" autoComplete="off" {...control} {...register('isbn', { validate: validateIsbn })} />
        )}
      </FormField>
      <FormField
        label="Total copies"
        required
        hint="A whole number from 1 to 999."
        error={errors.totalCopies?.message}
      >
        {(control) => (
          <input
            type="text"
            inputMode="numeric"
            className={`input ${styles.narrow}`}
            autoComplete="off"
            {...control}
            {...register('totalCopies', { validate: validateTotalCopies })}
          />
        )}
      </FormField>
      <div className={styles.actions}>
        <button type="submit" className="button button--primary" disabled={isSubmitting}>
          {submitLabel}
        </button>
        <Link to="/books" className="button">
          Cancel
        </Link>
      </div>
    </form>
  )
}
