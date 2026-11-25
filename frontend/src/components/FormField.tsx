import { useId, type ReactNode } from 'react'
import styles from './FormField.module.css'

/** Attributes the wrapped control must spread so label, hint and error are programmatically linked. */
export interface FieldControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  'aria-required'?: true
}

interface FormFieldProps {
  label: string
  /** Inline error text; associated with the control through `aria-describedby` and `aria-invalid`. */
  error?: string
  hint?: string
  required?: boolean
  /** Render prop so any control (input, select, textarea) can receive the accessibility attributes. */
  children: (control: FieldControlProps) => ReactNode
}

/** `<label>` + control + hint + inline error (architecture 9.1, 9.4; NFR-3). */
export function FormField({ label, error, hint, required = false, children }: FormFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ')

  const control: FieldControlProps = { id }
  if (describedBy) control['aria-describedby'] = describedBy
  if (error) control['aria-invalid'] = true
  if (required) control['aria-required'] = true

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required && <span className={styles.required}> (required)</span>}
      </label>
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
      {children(control)}
      {error && (
        <p id={errorId} className={styles.error}>
          <span aria-hidden="true">{'⚠'} </span>
          <span className="visually-hidden">Error: </span>
          {error}
        </p>
      )}
    </div>
  )
}
