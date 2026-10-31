import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { HELD_STATUSES, STATUS_LABELS, displayValue, formatDate, formatMoney, itemFields } from '../catalog'
import { ConfirmDeleteDialog } from '../components/Dialog'
import { EditProductDialog } from '../components/EditProductDialog'
import { Layout } from '../components/Layout'

type Data = {
  product: api.Product
  category: api.Category
  units: api.Unit[]
  lots: api.Lot[]
  stock: api.StockLevel[]
  locations: Map<number, string>
}

type Filter = 'held' | 'sold' | 'all'

const SOLD_STATUSES: api.UnitStatus[] = ['SOLD', 'CLOSED']

export function ProductPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<Filter>('held')
  const [open, setOpen] = useState<'edit' | 'delete' | null>(null)
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false
    async function load() {
      const product = await api.fetchProduct(id)
      // The lists hold every product's stock; this page keeps only this product's rows.
      const [category, units, lots, stock, locations] = await Promise.all([
        api.fetchCategory(product.categoryId),
        api.fetchUnits(),
        api.fetchLots(),
        api.fetchStock(),
        api.fetchLocations(),
      ])
      return {
        product,
        category,
        units: units.filter((u) => u.productId === id).sort((a, b) => b.receivedDate.localeCompare(a.receivedDate)),
        lots: lots.filter((l) => l.productId === id).sort((a, b) => b.receivedDate.localeCompare(a.receivedDate)),
        stock: stock.filter((s) => s.productId === id),
        locations: new Map(locations.map((l) => [l.id, l.name])),
      }
    }
    load()
      .then((d) => !cancelled && setData(d))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id, reloads])

  if (error) {
    return (
      <Layout>
        <div className="card error-box" role="alert">
          <p>{error}</p>
          <Link to="/catalog">Back to catalog</Link>
        </div>
      </Layout>
    )
  }
  if (!data) {
    return (
      <Layout>
        <p className="muted">Loading product…</p>
      </Layout>
    )
  }

  const { product, category, units, lots, stock, locations } = data
  const isUnit = category.trackingMode === 'UNIT'
  const productFields = category.fields.filter((f) => f.scope === 'PRODUCT')
  const pieceFields = itemFields(category)
  const inStock = stock.reduce((sum, s) => sum + s.quantity, 0)
  const location = (locationId: number) => locations.get(locationId) ?? '—'

  const held = units.filter((u) => HELD_STATUSES.includes(u.status))
  const sold = units.filter((u) => SOLD_STATUSES.includes(u.status))
  const shownUnits = filter === 'held' ? held : filter === 'sold' ? sold : units
  const openLots = lots.filter((l) => l.quantityRemaining > 0)
  const shownLots = filter === 'all' ? lots : openLots

  return (
    <Layout>
      <nav aria-label="Breadcrumb" className="breadcrumb">
        <Link to="/catalog">Catalog</Link>
        <span aria-hidden="true">/</span>
        <span>{category.name}</span>
      </nav>

      <div className="page-head align-start">
        <div className="stack tight">
          <h1 className="title-lg">{product.name}</h1>
          <div className="title-row small">
            <span className="sku-chip">{product.sku}</span>
            <span className="muted">
              {category.name} · {isUnit ? 'counted one by one' : 'counted by quantity'}
            </span>
          </div>
        </div>
        {isOwner && (
          <div className="actions wrap">
            <button className="secondary" onClick={() => setOpen('edit')}>
              Edit
            </button>
            <button className="secondary danger-text" onClick={() => setOpen('delete')}>
              Delete
            </button>
            <Link className="button" to={`/stock/new?product=${product.id}`}>
              {isUnit ? 'Add pieces' : 'Add stock'}
            </Link>
          </div>
        )}
      </div>

      <div className="summary-grid">
        <section className="card section">
          <h2 className="h-sm">Product details</h2>
          {productFields.length === 0 ? (
            <p className="muted">This category has no product fields.</p>
          ) : (
            <dl className="details">
              {productFields.map((f) => (
                <div key={f.key}>
                  <dt>{f.label}</dt>
                  <dd>{displayValue(f, product.attributes[f.key])}</dd>
                </div>
              ))}
            </dl>
          )}
        </section>
        <section className="card section">
          <h2 className="h-sm">In stock</h2>
          <div className="big-number">
            <span>{inStock.toLocaleString()}</span>
            <span className="muted">{inStock === 1 ? 'piece' : 'pieces'}</span>
          </div>
          <div className="stack tight">
            {stock.map((s) => (
              <div className="spread" key={s.locationId}>
                <span className="muted-strong">{s.locationName}</span>
                <span className="strong num">{s.quantity.toLocaleString()}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="card table-card">
        <div className="card-head center">
          <h2>{isUnit ? 'Pieces' : 'Batches'}</h2>
          <div className="segmented" role="group" aria-label={isUnit ? 'Show pieces' : 'Show batches'}>
            {isUnit ? (
              <>
                <button aria-pressed={filter === 'held'} onClick={() => setFilter('held')}>
                  In stock · {held.length}
                </button>
                <button aria-pressed={filter === 'sold'} onClick={() => setFilter('sold')}>
                  Sold · {sold.length}
                </button>
                <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>
                  All · {units.length}
                </button>
              </>
            ) : (
              <>
                <button aria-pressed={filter !== 'all'} onClick={() => setFilter('held')}>
                  With stock left · {openLots.length}
                </button>
                <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>
                  All · {lots.length}
                </button>
              </>
            )}
          </div>
        </div>

        {isUnit ? (
          shownUnits.length === 0 ? (
            <p className="muted pad">No pieces here.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    {pieceFields.map((f) => (
                      <th key={f.key} className={f.type === 'NUMBER' ? 'num' : undefined}>
                        {f.label}
                      </th>
                    ))}
                    <th className="num">Cost</th>
                    <th>Location</th>
                    <th>Status</th>
                    <th>Received</th>
                  </tr>
                </thead>
                <tbody>
                  {shownUnits.map((u) => (
                    <tr key={u.id}>
                      {pieceFields.map((f) => (
                        <td key={f.key} className={f.type === 'NUMBER' ? 'num' : f.type === 'TEXT' ? 'mono-ish' : undefined}>
                          {displayValue(f, u.attributes[f.key])}
                        </td>
                      ))}
                      <td className="num">{formatMoney(u.unitCost)}</td>
                      <td className="muted-strong">{location(u.locationId)}</td>
                      <td>
                        <span className={`status status-${u.status.toLowerCase()}`}>{STATUS_LABELS[u.status]}</span>
                      </td>
                      <td className="muted-strong nowrap">{formatDate(u.receivedDate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        ) : shownLots.length === 0 ? (
          <p className="muted pad">No batches here.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Batch</th>
                  <th className="num">Received</th>
                  <th className="num">Left</th>
                  <th className="num">Cost each</th>
                  <th>Location</th>
                  <th>Received on</th>
                </tr>
              </thead>
              <tbody>
                {shownLots.map((l) => (
                  <tr key={l.id}>
                    <td className="sku">{l.id}</td>
                    <td className="num">{l.quantityReceived.toLocaleString()}</td>
                    <td className="num strong">{l.quantityRemaining.toLocaleString()}</td>
                    <td className="num">{formatMoney(l.unitCost)}</td>
                    <td className="muted-strong">{location(l.locationId)}</td>
                    <td className="muted-strong nowrap">{formatDate(l.receivedDate)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {open === 'edit' && (
        <EditProductDialog
          product={product}
          category={category}
          onClose={() => setOpen(null)}
          onSaved={() => setReloads((n) => n + 1)}
        />
      )}
      {open === 'delete' && (
        <ConfirmDeleteDialog
          title={`Delete product ${product.sku}?`}
          confirmLabel="Delete product"
          message={
            <p>
              “{product.name}” moves to the trash and can be restored. A product that still has stock cannot be deleted.
              Past purchases and sales keep it.
            </p>
          }
          onClose={() => setOpen(null)}
          onConfirm={async (reason) => {
            await api.deleteProduct(product.id, reason)
            navigate('/catalog', { replace: true })
          }}
        />
      )}
    </Layout>
  )
}
