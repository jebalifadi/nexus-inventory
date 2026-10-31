import { useState, type FormEvent } from 'react'
import { createCategory, type Category, type FieldScope, type FieldType, type TrackingMode } from '../api'

type Props = { onCreated: (category: Category) => void; onCancel: () => void }

/** A field row while editing; choices are typed as one comma-separated text. */
type FieldDraft = {
  label: string
  type: FieldType
  scope: FieldScope
  required: boolean
  choices: string
  /** Part of the product name; name parts follow the order of the fields. */
  inName: boolean
}

const emptyField = (): FieldDraft => ({
  label: '',
  type: 'TEXT',
  scope: 'PRODUCT',
  required: false,
  choices: '',
  inName: false,
})

const TYPE_LABELS: Record<FieldType, string> = {
  TEXT: 'Text',
  NUMBER: 'Number',
  CHOICE: 'Choice list',
  BOOLEAN: 'Yes / no',
  DATE: 'Date',
}

/** Creates a category ("Phones") with its own fields ("Model", "Storage", "IMEI"). */
export function NewCategoryForm({ onCreated, onCancel }: Props) {
  const [name, setName] = useState('')
  const [trackingMode, setTrackingMode] = useState<TrackingMode>('LOT')
  const [fields, setFields] = useState<FieldDraft[]>([{ ...emptyField(), label: 'Model', required: true, inName: true }])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const updateField = (index: number, change: Partial<FieldDraft>) =>
    setFields((all) => all.map((f, i) => (i === index ? { ...f, ...change } : f)))

  // Lots have no per-piece fields, so every field belongs to the product there.
  const isProductField = (f: FieldDraft) => trackingMode === 'LOT' || f.scope === 'PRODUCT'
  const nameParts = fields.filter((f) => f.label.trim() && f.inName && isProductField(f)).map((f) => f.label.trim())

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const category = await createCategory({
        name,
        trackingMode,
        fields: fields
          .filter((f) => f.label.trim())
          .map((f) => ({
            label: f.label,
            type: f.type,
            // Lots are identical goods, so they have no per-item fields.
            scope: trackingMode === 'LOT' ? 'PRODUCT' : f.scope,
            required: f.required,
            inName: f.inName && isProductField(f),
            choices: f.type === 'CHOICE' ? f.choices.split(',').map((c) => c.trim()).filter(Boolean) : [],
          })),
      })
      onCreated(category)
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <form className="subform" onSubmit={handleSubmit}>
      <h3>New category</h3>

      <label>
        Name
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Phones" required autoFocus />
      </label>

      <fieldset>
        <legend>How is stock counted?</legend>
        <label className="choice">
          <input type="radio" checked={trackingMode === 'LOT'} onChange={() => setTrackingMode('LOT')} />
          <span>
            <strong>By quantity</strong> <span className="muted">— identical goods, e.g. 10 pairs of sneakers</span>
          </span>
        </label>
        <label className="choice">
          <input type="radio" checked={trackingMode === 'UNIT'} onChange={() => setTrackingMode('UNIT')} />
          <span>
            <strong>One by one</strong> <span className="muted">— each item has its own details, e.g. a phone's IMEI</span>
          </span>
        </label>
      </fieldset>

      <fieldset>
        <legend>Fields</legend>
        <p className="muted small">
          Tick <strong>In name</strong> for the fields that make up the product name, e.g. Model and Storage → “iPhone 13 ·
          128 GB”. The other fields appear as their own columns.
          {trackingMode === 'UNIT' && ' “Each item” fields (serial number, battery…) are filled in per piece when you add stock.'}
        </p>
        {fields.map((field, i) => (
          <div className="field-row" key={i}>
            <input
              aria-label="Field label"
              placeholder="Label, e.g. Storage"
              value={field.label}
              onChange={(e) => updateField(i, { label: e.target.value })}
            />
            <select aria-label="Field type" value={field.type} onChange={(e) => updateField(i, { type: e.target.value as FieldType })}>
              {Object.entries(TYPE_LABELS).map(([value, text]) => (
                <option key={value} value={value}>{text}</option>
              ))}
            </select>
            {trackingMode === 'UNIT' && (
              <select aria-label="Field belongs to" value={field.scope} onChange={(e) => updateField(i, { scope: e.target.value as FieldScope })}>
                <option value="PRODUCT">Product</option>
                <option value="ITEM">Each item</option>
              </select>
            )}
            <label className="inline">
              <input
                type="checkbox"
                checked={field.inName && isProductField(field)}
                disabled={!isProductField(field)}
                onChange={(e) => updateField(i, { inName: e.target.checked })}
              />
              In name
            </label>
            <label className="inline">
              <input type="checkbox" checked={field.required} onChange={(e) => updateField(i, { required: e.target.checked })} />
              Required
            </label>
            <button type="button" className="secondary icon" aria-label="Remove field" onClick={() => setFields((all) => all.filter((_, j) => j !== i))}>
              ×
            </button>
            {field.type === 'CHOICE' && (
              <input
                className="choices"
                aria-label="Choices"
                placeholder="Choices, separated by commas: 64 GB, 128 GB, 256 GB"
                value={field.choices}
                onChange={(e) => updateField(i, { choices: e.target.value })}
              />
            )}
          </div>
        ))}
        <button type="button" className="secondary" onClick={() => setFields((all) => [...all, emptyField()])}>
          + Add field
        </button>
        <div className="preview">
          <span className="eyebrow">Product names will look like</span>
          <strong>{nameParts.length ? nameParts.join(' · ') : 'All product fields (tick “In name” to choose)'}</strong>
        </div>
      </fieldset>

      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Create category'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  )
}
