import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { ActionForm, ConfirmDeleteDialog, Dialog } from '../components/Dialog'
import { FieldInput } from '../components/FieldInput'
import { Layout } from '../components/Layout'

type Data = { categories: api.Category[]; products: api.Product[] }

/** Which dialog is open, and for what. */
type Open =
  | { kind: 'renameCategory'; category: api.Category }
  | { kind: 'deleteCategory'; category: api.Category }
  | { kind: 'editProduct'; product: api.Product; category: api.Category }
  | { kind: 'deleteProduct'; product: api.Product }
  | null

/** Shown after a change; `undo` is offered after a delete, since deletes go to the trash. */
type Notice = { text: string; undo?: () => Promise<unknown> }

export function CatalogPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<Open>(null)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.fetchCategories(), api.fetchProducts()])
      .then(([categories, products]) => !cancelled && setData({ categories, products }))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [reloads])

  const reload = () => setReloads((n) => n + 1)
  const close = () => setOpen(null)

  async function undo(notice: Notice) {
    try {
      await notice.undo?.()
      setNotice({ text: 'Restored.' })
      reload()
    } catch (e) {
      setNotice({ text: (e as Error).message })
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Catalog</h1>
        {isOwner && (
          <Link className="button" to="/stock/new">
            New product
          </Link>
        )}
      </div>

      {notice && (
        <div className="card notice" role="status">
          <span>{notice.text}</span>
          {notice.undo && (
            <button className="secondary" onClick={() => undo(notice)}>
              Undo
            </button>
          )}
          <button className="secondary icon" aria-label="Dismiss" onClick={() => setNotice(null)}>
            ×
          </button>
        </div>
      )}

      {error && (
        <div className="card error-box" role="alert">
          <p>{error}</p>
        </div>
      )}
      {!data && !error && <p className="muted">Loading catalog…</p>}

      {data && data.categories.length === 0 && (
        <div className="card empty">
          <p>No categories yet.</p>
          <p className="muted">Create one while adding a new product.</p>
        </div>
      )}

      {data?.categories.map((category) => {
        const products = data.products
          .filter((p) => p.categoryId === category.id)
          .sort((a, b) => a.sku.localeCompare(b.sku))
        return (
          <section className="card section" key={category.id}>
            <div className="section-head">
              <div>
                <h2>{category.name}</h2>
                <p className="muted small">
                  {category.trackingMode === 'UNIT' ? 'Counted one by one' : 'Counted by quantity'}
                  {category.fields.length > 0 && ` · fields: ${category.fields.map((f) => f.label).join(', ')}`}
                </p>
              </div>
              {isOwner && (
                <div className="actions">
                  <button className="secondary" onClick={() => setOpen({ kind: 'renameCategory', category })}>
                    Rename
                  </button>
                  <button className="secondary danger-text" onClick={() => setOpen({ kind: 'deleteCategory', category })}>
                    Delete
                  </button>
                </div>
              )}
            </div>

            {products.length === 0 ? (
              <p className="muted">No products in this category.</p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>SKU</th>
                      <th>Name</th>
                      {isOwner && <th className="num">Actions</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {products.map((product) => (
                      <tr key={product.id}>
                        <td className="sku">{product.sku}</td>
                        <td>{product.name}</td>
                        {isOwner && (
                          <td className="num row-actions">
                            <button className="secondary" onClick={() => setOpen({ kind: 'editProduct', product, category })}>
                              Edit
                            </button>
                            <button className="secondary danger-text" onClick={() => setOpen({ kind: 'deleteProduct', product })}>
                              Delete
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )
      })}

      {open?.kind === 'renameCategory' && (
        <Dialog title={`Rename “${open.category.name}”`} onClose={close}>
          <ActionForm
            submitLabel="Save"
            busyLabel="Saving…"
            onClose={close}
            onSubmit={async (form) => {
              await api.renameCategory(open.category.id, String(form.get('name')))
              setNotice({ text: 'Category renamed.' })
              reload()
            }}
          >
            <label>
              Name
              <input name="name" defaultValue={open.category.name} maxLength={100} required autoFocus />
            </label>
            <p className="muted small">Fields and how stock is counted cannot change: existing products and stock depend on them.</p>
          </ActionForm>
        </Dialog>
      )}

      {open?.kind === 'deleteCategory' && (
        <ConfirmDeleteDialog
          title={`Delete category “${open.category.name}”?`}
          confirmLabel="Delete category"
          message={
            <p>
              It moves to the trash and can be restored. A category that still has products cannot be deleted:
              delete its products first.
            </p>
          }
          onClose={close}
          onConfirm={async (reason) => {
            const { id, name } = open.category
            await api.deleteCategory(id, reason)
            setNotice({ text: `Deleted category “${name}”.`, undo: () => api.restoreCategory(id) })
            reload()
          }}
        />
      )}

      {open?.kind === 'editProduct' && (
        <EditProductDialog
          product={open.product}
          category={open.category}
          onClose={close}
          onSaved={(p) => {
            setNotice({ text: `Saved ${p.sku}.` })
            reload()
          }}
        />
      )}

      {open?.kind === 'deleteProduct' && (
        <ConfirmDeleteDialog
          title={`Delete product ${open.product.sku}?`}
          confirmLabel="Delete product"
          message={
            <p>
              “{open.product.name}” moves to the trash and can be restored. A product that still has stock cannot be
              deleted. Past purchases and sales keep it.
            </p>
          }
          onClose={close}
          onConfirm={async (reason) => {
            const { id, sku } = open.product
            await api.deleteProduct(id, reason)
            setNotice({ text: `Deleted product ${sku}.`, undo: () => api.restoreProduct(id) })
            reload()
          }}
        />
      )}
    </Layout>
  )
}

type EditProps = {
  product: api.Product
  category: api.Category
  onClose: () => void
  onSaved: (product: api.Product) => void
}

/** Changes a product's SKU and product fields. Its category stays the same. */
function EditProductDialog({ product, category, onClose, onSaved }: EditProps) {
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
        <p className="muted small">Category: {category.name} (cannot change). The name is rebuilt from the fields.</p>
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
