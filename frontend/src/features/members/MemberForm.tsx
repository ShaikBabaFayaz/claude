import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import type { MemberRequest } from '../../api/types'
import { FormField } from '../../components/FormField'
import { getErrorMessage } from '../../lib/errorMessages'
import { pickFieldErrors } from '../../lib/fieldErrors'
import { validateEmail, validateMemberName } from '../../lib/validators'
import styles from './MemberForm.module.css'

export interface MemberFormValues {
  name: string
  email: string
}

export const EMPTY_MEMBER_FORM: MemberFormValues = { name: '', email: '' }

/** Form fields in page order: the first one with an error receives focus (AC-3.FE.2). */
const FIELD_ORDER = ['name', 'email'] as const

const SUMMARY_MESSAGE = 'The member was not saved. Fix the fields marked below and try again.'

interface MemberFormProps {
  initialValues?: MemberFormValues
  submitLabel: string
  /** Sends the request. Reject with an `ApiError` to have the server's messages shown inline. */
  onSubmit: (request: MemberRequest) => Promise<unknown>
}

/**
 * Register/edit member form (AC-3.FE.2). Client checks (name 1..100 characters, the email rule of
 * AC-3.BE.2) block the request and show an inline error per field. A server 400 or a 409 duplicate
 * email is applied to its field with the entered values kept and focus on the first such field; an
 * error without a field becomes a form-level message (architecture 9.3).
 */
export function MemberForm({ initialValues = EMPTY_MEMBER_FORM, submitLabel, onSubmit }: MemberFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<MemberFormValues>({ defaultValues: initialValues })
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
        await onSubmit({ name: values.name.trim(), email: values.email.trim() })
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
      <FormField label="Name" required error={errors.name?.message}>
        {(control) => (
          <input
            type="text"
            className="input"
            autoComplete="off"
            {...control}
            {...register('name', { validate: validateMemberName })}
          />
        )}
      </FormField>
      <FormField
        label="Email"
        required
        hint="For example name@example.org. Two members cannot share an email address."
        error={errors.email?.message}
      >
        {(control) => (
          <input
            type="text"
            inputMode="email"
            className="input"
            autoComplete="off"
            {...control}
            {...register('email', { validate: validateEmail })}
          />
        )}
      </FormField>
      <div className={styles.actions}>
        <button type="submit" className="button button--primary" disabled={isSubmitting}>
          {submitLabel}
        </button>
        <Link to="/members" className="button">
          Cancel
        </Link>
      </div>
    </form>
  )
}
