import { useState, type FormEvent } from 'react'
import { createCategory, type Category, type FieldScope, type FieldType, type TrackingMode } from '../api'
import { NAME_SEPARATOR } from '../catalog'

type Props = { onCreated: (category: Category) => void; onCancel: () => void }

/** A field while editing; choices are typed as one comma-separated text. */
type FieldDraft = {
  label: string
  type: FieldType
  /**
   * PRODUCT: the same for every piece. ITEM: different for each piece.
   * Null until the person chooses, for one-by-one categories: there is no safe default.
   */
  scope: FieldScope | null
  required: boolean
  choices: string
  /** Part of the product name; name parts follow the order of the fields. */
  inName: boolean
}

const emptyField = (): FieldDraft => ({
  label: '',
  type: 'TEXT',
  scope: null,
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

/** A field named like a price is usually a mistake: cost is entered per piece when stock is added. */
const LOOKS_LIKE_PRICE = /preis|price|prix|cost|kosten|\b[ev]k\b/i

/** Creates a category ("Phones") with its own fields ("Model", "Storage", "IMEI"). */
export function NewCategoryForm({ onCreated, onCancel }: Props) {
  const [name, setName] = useState('')
  const [trackingMode, setTrackingMode] = useState<TrackingMode>('LOT')
  const [fields, setFields] = useState<FieldDraft[]>([
    { ...emptyField(), label: 'Model', scope: 'PRODUCT', required: true, inName: true },
  ])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const isUnit = trackingMode === 'UNIT'
  const updateField = (index: number, change: Partial<FieldDraft>) =>
    setFields((all) => all.map((f, i) => (i === index ? { ...f, ...change } : f)))

  // By quantity: identical goods, so every field is the same for every piece.
  const scopeOf = (f: FieldDraft): FieldScope | null => (isUnit ? f.scope : 'PRODUCT')
  const named = fields.filter((f) => f.label.trim())
  const nameParts = named.filter((f) => scopeOf(f) === 'PRODUCT' && f.inName).map((f) => f.label.trim())
  const columns = named.filter((f) => scopeOf(f) === 'PRODUCT' && !f.inName).map((f) => f.label.trim())
  const perPiece = named.filter((f) => scopeOf(f) === 'ITEM').map((f) => f.label.trim())
  const undecided = named.filter((f) => scopeOf(f) === null).map((f) => f.label.trim())

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (undecided.length) {
      setError(`Choose for ${undecided.map((l) => `“${l}”`).join(', ')}: the same for every piece, or different for each piece.`)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const category = await createCategory({
        name,
        trackingMode,
        fields: named.map((f) => {
          const scope = scopeOf(f) ?? 'PRODUCT'
          return {
            label: f.label,
            type: f.type,
            scope,
            required: f.required,
            inName: scope === 'PRODUCT' && f.inName,
            choices: f.type === 'CHOICE' ? f.choices.split(',').map((c) => c.trim()).filter(Boolean) : [],
          }
        }),
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
          <input type="radio" name="tracking" checked={trackingMode === 'LOT'} onChange={() => setTrackingMode('LOT')} />
          <span>
            <strong>By quantity</strong> <span className="muted">— identical goods, e.g. 10 pairs of sneakers</span>
          </span>
        </label>
        <label className="choice">
          <input type="radio" name="tracking" checked={isUnit} onChange={() => setTrackingMode('UNIT')} />
          <span>
            <strong>One by one</strong> <span className="muted">— each piece has its own details, e.g. a phone’s IMEI and battery</span>
          </span>
        </label>
      </fieldset>

      <fieldset>
        <legend>Fields</legend>
        <p className="muted small">
          {isUnit
            ? 'For each field, choose whether it is the same for every piece (model, storage, colour) or different for each piece (IMEI, battery, condition, repairs).'
            : 'Fields describe the product, e.g. Model, Size, Colour. They are the same for every piece.'}
        </p>

        {fields.map((field, i) => {
          const scope = scopeOf(field)
          return (
            <div className="field-card" key={i}>
              <div className="field-card-row">
                <label className="grow">
                  <span className="sr-only">Field name</span>
                  <input
                    placeholder="Field name, e.g. Storage"
                    value={field.label}
                    onChange={(e) => updateField(i, { label: e.target.value })}
                  />
                </label>
                <label>
                  <span className="sr-only">Type</span>
                  <select value={field.type} onChange={(e) => updateField(i, { type: e.target.value as FieldType })}>
                    {Object.entries(TYPE_LABELS).map(([value, text]) => (
                      <option key={value} value={value}>
                        {text}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  className="secondary icon"
                  aria-label={`Remove field ${field.label || i + 1}`}
                  onClick={() => setFields((all) => all.filter((_, j) => j !== i))}
                >
                  ×
                </button>
              </div>

              {field.type === 'CHOICE' && (
                <label>
                  <span className="sr-only">Choices</span>
                  <input
                    placeholder="Choices, separated by commas: 64 GB, 128 GB, 256 GB"
                    value={field.choices}
                    onChange={(e) => updateField(i, { choices: e.target.value })}
                  />
                </label>
              )}

              {isUnit && (
                <div className="scope-choice" role="radiogroup" aria-label={`${field.label || 'This field'} is`}>
                  <label className={scope === 'PRODUCT' ? 'selected' : undefined}>
                    <input
                      type="radio"
                      name={`scope-${i}`}
                      checked={scope === 'PRODUCT'}
                      onChange={() => updateField(i, { scope: 'PRODUCT' })}
                    />
                    <span>
                      <strong>The same for every piece</strong>
                      <span className="muted small">Model, storage, colour</span>
                    </span>
                  </label>
                  <label className={scope === 'ITEM' ? 'selected' : undefined}>
                    <input
                      type="radio"
                      name={`scope-${i}`}
                      checked={scope === 'ITEM'}
                      onChange={() => updateField(i, { scope: 'ITEM', inName: false })}
                    />
                    <span>
                      <strong>Different for each piece</strong>
                      <span className="muted small">IMEI, battery, condition, repairs</span>
                    </span>
                  </label>
                </div>
              )}

              <div className="field-card-row">
                {scope === 'PRODUCT' && (
                  <label className="inline">
                    <input type="checkbox" checked={field.inName} onChange={(e) => updateField(i, { inName: e.target.checked })} />
                    Part of the product name
                  </label>
                )}
                <label className="inline">
                  <input type="checkbox" checked={field.required} onChange={(e) => updateField(i, { required: e.target.checked })} />
                  Required
                </label>
              </div>

              {LOOKS_LIKE_PRICE.test(field.label) && (
                <p className="hint small">
                  You may not need this field: the cost of each piece is entered when you add stock, and Nexus works out
                  the real cost and profit from it.
                </p>
              )}
            </div>
          )
        })}

        <button type="button" className="secondary align-start" onClick={() => setFields((all) => [...all, emptyField()])}>
          + Add field
        </button>

        <div className="preview">
          <span className="eyebrow">How it will look</span>
          <dl className="placement">
            <div>
              <dt>Product name</dt>
              <dd>
                <strong>{nameParts.length ? nameParts.join(NAME_SEPARATOR) : columns.length ? columns.join(NAME_SEPARATOR) : 'The SKU'}</strong>
                {!nameParts.length && columns.length > 0 && (
                  <span className="muted small"> (nothing ticked: all “same for every piece” fields)</span>
                )}
              </dd>
            </div>
            {nameParts.length > 0 && columns.length > 0 && (
              <div>
                <dt>Columns</dt>
                <dd>{columns.join(', ')}</dd>
              </div>
            )}
            {isUnit && (
              <div>
                <dt>Filled in for each piece</dt>
                <dd>{perPiece.length ? perPiece.join(', ') : <span className="muted">None yet</span>}</dd>
              </div>
            )}
            {undecided.length > 0 && (
              <div>
                <dt>Not decided yet</dt>
                <dd className="warning-text">{undecided.join(', ')}</dd>
              </div>
            )}
          </dl>
        </div>
      </fieldset>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="actions">
        <button type="submit" disabled={busy}>
          {busy ? 'Saving…' : 'Create category'}
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
