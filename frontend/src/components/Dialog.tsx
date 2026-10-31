import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'

type Props = { title: string; onClose: () => void; children: ReactNode }

/**
 * A modal window, using the browser's <dialog>: it keeps keyboard focus inside, and Esc closes it.
 * Rendered only while open, so each opening starts fresh.
 */
export function Dialog({ title, onClose, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault() // Esc: let React close it, so state and screen stay in step
        onClose()
      }}
    >
      <h2>{title}</h2>
      {children}
    </dialog>
  )
}

type ConfirmProps = {
  title: string
  message: ReactNode
  confirmLabel: string
  /** Runs the action with the optional reason; throw to show the error and keep the dialog open. */
  onConfirm: (reason: string) => Promise<void>
  onClose: () => void
}

/** Asks "are you sure?" before deleting, with an optional reason kept in the trash. */
export function ConfirmDeleteDialog({ title, message, confirmLabel, onConfirm, onClose }: ConfirmProps) {
  return (
    <Dialog title={title} onClose={onClose}>
      <ActionForm
        submitLabel={confirmLabel}
        busyLabel="Deleting…"
        danger
        onSubmit={async (form) => onConfirm(String(form.get('reason') ?? ''))}
        onClose={onClose}
      >
        <div className="muted">{message}</div>
        <label>
          Reason <span className="muted">(optional, kept with the deleted record)</span>
          <input name="reason" maxLength={255} placeholder="e.g. created by mistake" />
        </label>
      </ActionForm>
    </Dialog>
  )
}

type ActionFormProps = {
  submitLabel: string
  busyLabel: string
  danger?: boolean
  onSubmit: (form: FormData) => Promise<void>
  onClose: () => void
  children: ReactNode
}

/** The form inside a dialog: shows a busy state and the backend's error, closes on success. */
export function ActionForm({ submitLabel, busyLabel, danger, onSubmit, onClose, children }: ActionFormProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onSubmit(new FormData(event.currentTarget))
      onClose() // success: the dialog goes away, so no state to reset
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <form className="stack" onSubmit={handleSubmit}>
      {children}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="actions">
        <button type="submit" disabled={busy} className={danger ? 'danger' : undefined}>
          {busy ? busyLabel : submitLabel}
        </button>
        <button type="button" className="secondary" onClick={onClose}>
          Cancel
        </button>
      </div>
    </form>
  )
}
