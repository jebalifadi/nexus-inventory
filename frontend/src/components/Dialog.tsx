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
  /** Shown on the button while it runs; "Deleting…" by default. */
  busyLabel?: string
  /** Common reasons offered as one-tap choices; the person can still type their own. */
  reasons?: string[]
  /** The action needs a reason (it is kept in the trash with the record). */
  requireReason?: boolean
  /** Runs the action with the reason; throw to show the error and keep the dialog open. */
  onConfirm: (reason: string) => Promise<void>
  onClose: () => void
}

/** Asks "are you sure?" before deleting, with a reason kept in the trash. */
export function ConfirmDeleteDialog({
  title,
  message,
  confirmLabel,
  busyLabel = 'Deleting…',
  reasons = [],
  requireReason = false,
  onConfirm,
  onClose,
}: ConfirmProps) {
  const [reason, setReason] = useState('')

  return (
    <Dialog title={title} onClose={onClose}>
      <ActionForm submitLabel={confirmLabel} busyLabel={busyLabel} danger onSubmit={() => onConfirm(reason)} onClose={onClose}>
        <div className="muted">{message}</div>
        {reasons.length > 0 && (
          <div className="reason-choices" role="group" aria-label="Common reasons">
            {reasons.map((r) => (
              <button
                key={r}
                type="button"
                className="secondary"
                aria-pressed={reason === r}
                onClick={() => setReason(r)}
              >
                {r}
              </button>
            ))}
          </div>
        )}
        <label>
          <span>
            Reason{' '}
            <span className="muted">{requireReason ? '(kept with the record in the trash)' : '(optional, kept with the deleted record)'}</span>
          </span>
          <input
            name="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={255}
            required={requireReason}
            placeholder={reasons.length ? 'Choose above or type your own' : 'e.g. created by mistake'}
          />
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
