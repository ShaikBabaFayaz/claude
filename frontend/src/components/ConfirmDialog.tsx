import { useEffect, useId, useRef, type ReactNode } from 'react'
import styles from './ConfirmDialog.module.css'

interface ConfirmDialogProps {
  open: boolean
  title: string
  /** Explanation shown under the title; also the dialog's accessible description. */
  children: ReactNode
  confirmLabel: string
  cancelLabel?: string
  /** Destructive actions (delete, deactivate) use the danger style. */
  danger?: boolean
  /** True while the confirmed action is running: both buttons are disabled. */
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Confirmation dialog on the native `<dialog>` element opened with `showModal()`, which gives a
 * focus trap and Esc-to-cancel for free (architecture 9.4, DR-11). Focus starts on Cancel (the safe
 * choice) and returns to the element that opened the dialog, but only if that element still
 * exists (for example the Delete button of a row that was just removed is gone).
 */
export function ConfirmDialog(props: ConfirmDialogProps) {
  if (!props.open) return null
  return <OpenConfirmDialog {...props} />
}

function OpenConfirmDialog({
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancel',
  danger = false,
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (!dialog.open) dialog.showModal()
    cancelRef.current?.focus()
    return () => {
      if (dialog.open) dialog.close()
      if (opener && opener.isConnected) opener.focus()
    }
  }, [])

  return (
    <dialog
      ref={dialogRef}
      className={styles.dialog}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => {
        // Esc: the parent owns the open state, so keep the dialog open until it says otherwise.
        event.preventDefault()
        if (!busy) onCancel()
      }}
    >
      <h2 id={titleId} className={styles.title}>
        {title}
      </h2>
      <div id={descriptionId} className={styles.body}>
        {children}
      </div>
      <div className={styles.actions}>
        <button type="button" ref={cancelRef} className="button" disabled={busy} onClick={onCancel}>
          {cancelLabel}
        </button>
        <button
          type="button"
          className={`button ${danger ? 'button--danger' : 'button--primary'}`}
          disabled={busy}
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
