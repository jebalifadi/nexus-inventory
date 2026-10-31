import { useState } from 'react'
import * as api from '../api'
import { NAME_SEPARATOR, nameFields } from '../catalog'
import { ActionForm, Dialog } from './Dialog'

type Props = {
  category: api.Category
  /** A product of this category, used to preview the name with real values. */
  example?: api.Product
  onClose: () => void
  onSaved: (category: api.Category) => void
}

/** Rename a category and choose which fields form its product names, in which order. */
export function EditCategoryDialog({ category, example, onClose, onSaved }: Props) {
  const [name, setName] = useState(category.name)
  // Name fields as an ordered list of keys; every other product field becomes a column.
  const [inName, setInName] = useState<string[]>(nameFields(category).map((f) => f.key))

  const productFields = category.fields.filter((f) => f.scope === 'PRODUCT')
  const ordered = [
    ...inName.map((key) => category.fields.find((f) => f.key === key)!),
    ...category.fields.filter((f) => !inName.includes(f.key)),
  ]

  const toggle = (key: string) =>
    setInName((keys) => (keys.includes(key) ? keys.filter((k) => k !== key) : [...keys, key]))
  const move = (key: string, step: -1 | 1) =>
    setInName((keys) => {
      const i = keys.indexOf(key)
      const j = i + step
      if (j < 0 || j >= keys.length) return keys
      const next = [...keys]
      ;[next[i], next[j]] = [next[j], next[i]]
      return next
    })

  const preview =
    inName
      .map((key) => (example ? example.attributes[key] : category.fields.find((f) => f.key === key)?.label))
      .filter(Boolean)
      .join(NAME_SEPARATOR) || (example?.sku ?? 'The SKU')

  return (
    <Dialog title={`Edit category “${category.name}”`} onClose={onClose}>
      <ActionForm
        submitLabel="Save"
        busyLabel="Saving…"
        onClose={onClose}
        onSubmit={async () => onSaved(await api.updateCategory(category.id, { name, nameFields: inName }))}
      >
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus />
        </label>

        <div className="stack tight">
          <span className="label">Product name</span>
          <span className="muted small">
            Tick the fields that make up the name, in order. The others appear as their own columns.
          </span>
          <table className="compact">
            <thead>
              <tr>
                <th>In name</th>
                <th>Field</th>
                <th>Belongs to</th>
                <th className="num">Order</th>
              </tr>
            </thead>
            <tbody>
              {ordered.map((field) => {
                const checked = inName.includes(field.key)
                const perPiece = field.scope === 'ITEM'
                const index = inName.indexOf(field.key)
                return (
                  <tr key={field.key}>
                    <td>
                      <input
                        type="checkbox"
                        className="check"
                        checked={checked}
                        disabled={perPiece}
                        onChange={() => toggle(field.key)}
                        aria-label={`Use ${field.label} in the name`}
                      />
                    </td>
                    <td className={perPiece ? 'muted' : undefined}>{field.label}</td>
                    <td className="muted">{perPiece ? 'Each piece' : 'Product'}</td>
                    <td className="num nowrap">
                      {checked && (
                        <>
                          <button type="button" className="secondary icon" disabled={index === 0} onClick={() => move(field.key, -1)} aria-label={`Move ${field.label} up`}>
                            ↑
                          </button>{' '}
                          <button type="button" className="secondary icon" disabled={index === inName.length - 1} onClick={() => move(field.key, 1)} aria-label={`Move ${field.label} down`}>
                            ↓
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {productFields.length < category.fields.length && (
            <span className="muted small">
              Per-piece fields can’t be in the product name: they differ for every piece.
            </span>
          )}
        </div>

        <div className="preview">
          <span className="eyebrow">Preview</span>
          <strong>{preview}</strong>
          <span className="muted small">Existing product names in this category update when you save.</span>
        </div>
      </ActionForm>
    </Dialog>
  )
}
