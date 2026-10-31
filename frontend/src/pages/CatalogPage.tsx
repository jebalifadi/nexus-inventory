import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { columnFields, displayValue, nameFields } from '../catalog'
import { ConfirmDeleteDialog } from '../components/Dialog'
import { EditCategoryDialog } from '../components/EditCategoryDialog'
import { EditProductDialog } from '../components/EditProductDialog'
import { Layout } from '../components/Layout'

type Data = { categories: api.Category[]; products: api.Product[]; stockByProduct: Map<number, number> }

/** Which dialog is open, and for what. */
type Open =
  | { kind: 'editCategory'; category: api.Category }
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
  const [search, setSearch] = useState('')
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.fetchCategories(), api.fetchProducts(), api.fetchStock()])
      .then(([categories, products, stock]) => {
        const stockByProduct = new Map<number, number>()
        for (const s of stock) stockByProduct.set(s.productId, (stockByProduct.get(s.productId) ?? 0) + s.quantity)
        if (!cancelled) setData({ categories, products, stockByProduct })
      })
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

  const query = search.trim().toLowerCase()
  const matches = (p: api.Product) =>
    !query || `${p.sku} ${p.name} ${Object.values(p.attributes).join(' ')}`.toLowerCase().includes(query)

  return (
    <Layout>
      <div className="page-head">
        <h1>Catalog</h1>
        <div className="head-tools">
          <input
            type="search"
            placeholder="Search SKU or name"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search catalog"
          />
          {isOwner && (
            <Link className="button" to="/stock/new">
              New product
            </Link>
          )}
        </div>
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
        const all = data.products.filter((p) => p.categoryId === category.id)
        const products = all.filter(matches).sort((a, b) => a.name.localeCompare(b.name) || a.sku.localeCompare(b.sku))
        if (query && products.length === 0) return null
        const columns = columnFields(category)
        const names = nameFields(category)
        const pieces = all.reduce((sum, p) => sum + (data.stockByProduct.get(p.id) ?? 0), 0)
        return (
          <section className="card table-card" key={category.id}>
            <div className="card-head">
              <div className="stack tight">
                <div className="title-row">
                  <h2>{category.name}</h2>
                  <span className="badge">{category.trackingMode === 'UNIT' ? 'One by one' : 'By quantity'}</span>
                  <span className="muted small">
                    {all.length} {all.length === 1 ? 'product' : 'products'} · {pieces} in stock
                  </span>
                </div>
                <div className="chips">
                  <span className="muted">Name from</span>
                  {names.length === 0 ? (
                    <span className="muted">SKU</span>
                  ) : (
                    names.map((f, i) => (
                      <span key={f.key} className="chip-wrap">
                        {i > 0 && <span aria-hidden="true">·</span>}
                        <span className="chip">{f.label}</span>
                      </span>
                    ))
                  )}
                  {isOwner && (
                    <button className="link" onClick={() => setOpen({ kind: 'editCategory', category })}>
                      Change
                    </button>
                  )}
                </div>
              </div>
              {isOwner && (
                <div className="actions">
                  <button className="secondary small-btn" onClick={() => setOpen({ kind: 'editCategory', category })}>
                    Edit category
                  </button>
                  <button className="secondary small-btn danger-text" onClick={() => setOpen({ kind: 'deleteCategory', category })}>
                    Delete
                  </button>
                </div>
              )}
            </div>

            {products.length === 0 ? (
              <p className="muted pad">No products in this category yet.</p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>SKU</th>
                      <th>Name</th>
                      {columns.map((f) => (
                        <th key={f.key}>{f.label}</th>
                      ))}
                      <th className="num">In stock</th>
                      {isOwner && (
                        <th className="num">
                          <span className="sr-only">Actions</span>
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {products.map((product) => (
                      <tr key={product.id}>
                        <td className="sku">{product.sku}</td>
                        <td>
                          <Link className="name-link" to={`/products/${product.id}`}>
                            {product.name}
                          </Link>
                        </td>
                        {columns.map((f) => (
                          <td key={f.key}>{displayValue(f, product.attributes[f.key])}</td>
                        ))}
                        <td className="num">{(data.stockByProduct.get(product.id) ?? 0).toLocaleString()}</td>
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

      {open?.kind === 'editCategory' && (
        <EditCategoryDialog
          category={open.category}
          example={data?.products.find((p) => p.categoryId === open.category.id)}
          onClose={close}
          onSaved={(c) => {
            setNotice({ text: `Saved category “${c.name}”.` })
            reload()
          }}
        />
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
