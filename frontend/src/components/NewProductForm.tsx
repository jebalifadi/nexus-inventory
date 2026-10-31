import { useState, type FormEvent } from 'react'
import { createProduct, type Category, type Product } from '../api'
import { FieldInput } from './FieldInput'
import { NewCategoryForm } from './NewCategoryForm'

type Props = {
  categories: Category[]
  onCategoryCreated: (category: Category) => void
  onCreated: (product: Product) => void
  onCancel: () => void
}

/** Creates a product (one SKU) of a category, filling in the category's product fields. */
export function NewProductForm({ categories, onCategoryCreated, onCreated, onCancel }: Props) {
  const [categoryId, setCategoryId] = useState<number | null>(categories.length === 1 ? categories[0].id : null)
  const [creatingCategory, setCreatingCategory] = useState(categories.length === 0)
  const [sku, setSku] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const category = categories.find((c) => c.id === categoryId)
  const productFields = category?.fields.filter((f) => f.scope === 'PRODUCT') ?? []

  // Shown instead of the product form, never inside it: HTML forms cannot be nested.
  if (creatingCategory) {
    return (
      <NewCategoryForm
        onCreated={(created) => {
          onCategoryCreated(created)
          setCategoryId(created.id)
          setValues({})
          setCreatingCategory(false)
        }}
        onCancel={() => (categories.length === 0 ? onCancel() : setCreatingCategory(false))}
      />
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!category) return
    setBusy(true)
    setError(null)
    try {
      // Send only the fields of the chosen category, and leave out empty optional ones.
      const attributes = Object.fromEntries(
        productFields.filter((f) => values[f.key]?.trim()).map((f) => [f.key, values[f.key]]),
      )
      onCreated(await createProduct({ categoryId: category.id, sku, attributes }))
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <form className="subform" onSubmit={handleSubmit}>
      <h3>New product</h3>

      <div className="row">
        <label>
          Category
          <select
            value={categoryId ?? ''}
            onChange={(e) => {
              setCategoryId(e.target.value ? Number(e.target.value) : null)
              setValues({})
            }}
            required
          >
            <option value="">Choose…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.trackingMode === 'UNIT' ? 'one by one' : 'by quantity'})
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="secondary align-end" onClick={() => setCreatingCategory(true)}>
          + New category
        </button>
      </div>

      <label>
        SKU <span className="muted">(your product code: letters, digits, “.”, “_”, “-”)</span>
        <input
          value={sku}
          onChange={(e) => setSku(e.target.value)}
          placeholder="IPH14-128-BLK"
          pattern="[A-Za-z0-9._\-]+"
          maxLength={64}
          required
        />
      </label>

      {productFields.map((field) => (
        <FieldInput
          key={field.key}
          field={field}
          value={values[field.key] ?? ''}
          onChange={(value) => setValues((v) => ({ ...v, [field.key]: value }))}
        />
      ))}

      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={busy || !category}>{busy ? 'Saving…' : 'Create product'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  )
}
