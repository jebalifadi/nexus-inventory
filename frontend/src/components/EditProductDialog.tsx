import { useState } from 'react'
import * as api from '../api'
import { ActionForm, Dialog } from './Dialog'
import { FieldInput } from './FieldInput'

type Props = {
  product: api.Product
  category: api.Category
  onClose: () => void
  onSaved: (product: api.Product) => void
}

/** Changes a product's SKU and product fields. Its category stays the same. */
export function EditProductDialog({ product, category, onClose, onSaved }: Props) {
  const [sku, setSku] = useState(product.sku)
  const [values, setValues] = useState<Record<string, string>>(product.attributes)
  const productFields = category.fields.filter((f) => f.scope === 'PRODUCT')

  return (
    <Dialog title={`Edit ${product.sku}`} onClose={onClose}>
      <ActionForm
        submitLabel="Save"
        busyLabel="Saving…"
        onClose={onClose}
        onSubmit={async () => {
          const attributes = Object.fromEntries(
            productFields.filter((f) => values[f.key]?.trim()).map((f) => [f.key, values[f.key]]),
          )
          onSaved(await api.updateProduct(product.id, { sku, attributes }))
        }}
      >
        <p className="muted small">Category: {category.name} (cannot change). The name is rebuilt from the name fields.</p>
        <label>
          SKU
          <input
            value={sku}
            onChange={(e) => setSku(e.target.value)}
            pattern="[A-Za-z0-9._\-]+"
            maxLength={64}
            required
            autoFocus
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
      </ActionForm>
    </Dialog>
  )
}
