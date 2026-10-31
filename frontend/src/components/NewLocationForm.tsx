import { useState, type FormEvent } from 'react'
import { createLocation, type Location } from '../api'

type Props = { onCreated: (location: Location) => void; onCancel: () => void }

/** Creates a place where stock is kept, for example "Berlin storage" in Germany. */
export function NewLocationForm({ onCreated, onCancel }: Props) {
  const [name, setName] = useState('')
  const [country, setCountry] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onCreated(await createLocation({ name, country }))
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <form className="subform" onSubmit={handleSubmit}>
      <h3>New location</h3>
      <div className="row">
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Berlin storage" required autoFocus />
        </label>
        <label>
          Country
          <input value={country} onChange={(e) => setCountry(e.target.value)} placeholder="Germany" required />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Create location'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  )
}
