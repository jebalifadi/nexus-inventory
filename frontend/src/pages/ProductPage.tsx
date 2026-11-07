import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { HELD_STATUSES, STATUS_LABELS, displayValue, formatDate, formatMoney, itemFields, formatNumber } from '../catalog'
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

/** Which dialog is open, and for what. */
type Open =
  | { kind: 'edit' }
  | { kind: 'delete' }
  | { kind: 'removeUnit'; unit: api.Unit }
  | { kind: 'removeLot'; lot: api.Lot }
  | null

const SOLD_STATUSES: api.UnitStatus[] = ['SOLD', 'CLOSED']

/** Offered as one-tap reasons when removing a piece or a batch. */
const REMOVE_REASONS = ['Created by mistake', 'Damaged', 'Lost']
/** These mean the goods are really gone: their cost counts as a loss (it can still be unticked). */
const LOSS_REASONS = ['Damaged', 'Lost']

/** "€335.00" or "€300.00 + 60.000 TND" for a write-off's cost. */
const formatCost = (cost: api.Money[]) => cost.map(formatMoney).join(' + ')

/** Same rules as the backend: sold or travelling pieces stay; a batch with sales stays. */
const canRemoveUnit = (u: api.Unit) => !['SOLD', 'CLOSED', 'IN_TRANSIT'].includes(u.status)
const canRemoveLot = (l: api.Lot) => l.quantityRemaining === l.quantityReceived && l.status !== 'IN_TRANSIT'

export function ProductPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<Filter>('held')
  const [open, setOpen] = useState<Open>(null)
  const [notice, setNotice] = useState<{ text: string; undo?: () => Promise<unknown> } | null>(null)
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
  const reload = () => setReloads((n) => n + 1)

  async function undo() {
    try {
      await notice?.undo?.()
      setNotice({ text: 'Restored.' })
      reload()
    } catch (e) {
      setNotice({ text: (e as Error).message })
    }
  }

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
            <button className="secondary" onClick={() => setOpen({ kind: 'edit' })}>
              Edit
            </button>
            <button className="secondary danger-text" onClick={() => setOpen({ kind: 'delete' })}>
              Delete
            </button>
            <Link className="button" to={`/stock/new?product=${product.id}`}>
              {isUnit ? 'Add pieces' : 'Add stock'}
            </Link>
          </div>
        )}
      </div>

      {notice && (
        <div className="card notice" role="status">
          <span>{notice.text}</span>
          {notice.undo && (
            <button className="secondary" onClick={undo}>
              Undo
            </button>
          )}
          <button className="secondary icon" aria-label="Dismiss" onClick={() => setNotice(null)}>
            ×
          </button>
        </div>
      )}

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
            <span>{formatNumber(inStock)}</span>
            <span className="muted">{inStock === 1 ? 'piece' : 'pieces'}</span>
          </div>
          <div className="stack tight">
            {stock.map((s) => (
              <div className="spread" key={s.locationId}>
                <span className="muted-strong">{s.locationName}</span>
                <span className="strong num">{formatNumber(s.quantity)}</span>
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
                    {isOwner && (
                      <th className="num">
                        <span className="sr-only">Actions</span>
                      </th>
                    )}
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
                      {isOwner && (
                        <td className="num row-actions">
                          {canRemoveUnit(u) && (
                            <button className="secondary danger-text" onClick={() => setOpen({ kind: 'removeUnit', unit: u })}>
                              Remove
                            </button>
                          )}
                        </td>
                      )}
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
                  {isOwner && (
                    <th className="num">
                      <span className="sr-only">Actions</span>
                    </th>
                  )}
                </tr>
              </thead>
              <tbody>
                {shownLots.map((l) => (
                  <tr key={l.id}>
                    <td className="sku">{l.id}</td>
                    <td className="num">{formatNumber(l.quantityReceived)}</td>
                    <td className="num strong">{formatNumber(l.quantityRemaining)}</td>
                    <td className="num">{formatMoney(l.unitCost)}</td>
                    <td className="muted-strong">{location(l.locationId)}</td>
                    <td className="muted-strong nowrap">{formatDate(l.receivedDate)}</td>
                    {isOwner && (
                      <td className="num row-actions">
                        {canRemoveLot(l) && (
                          <button className="secondary danger-text" onClick={() => setOpen({ kind: 'removeLot', lot: l })}>
                            Remove
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {open?.kind === 'edit' && (
        <EditProductDialog
          product={product}
          category={category}
          onClose={() => setOpen(null)}
          onSaved={reload}
        />
      )}
      {open?.kind === 'delete' && (
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
      {open?.kind === 'removeUnit' && (
        <ConfirmDeleteDialog
          title="Remove this piece?"
          confirmLabel="Remove piece"
          busyLabel="Removing…"
          reasons={REMOVE_REASONS}
          requireReason
          message={
            <p>
              {pieceSummary(open.unit, pieceFields)} ({formatMoney(open.unit.unitCost)}, {location(open.unit.locationId)}) leaves
              the stock and moves to the trash, where it can be restored.
            </p>
          }
          onClose={() => setOpen(null)}
          lossReasons={LOSS_REASONS}
          onConfirm={async (reason, loss) => {
            const { id } = open.unit
            if (loss) {
              const writeOff = await api.writeOffUnit(id, reason)
              setNotice({
                text: `Wrote off piece ${id} (${reason}): ${formatCost(writeOff.cost)} counted as a loss.`,
                undo: () => api.restoreUnit(id),
              })
            } else {
              await api.deleteUnit(id, reason)
              setNotice({ text: `Removed piece ${id} (${reason}).`, undo: () => api.restoreUnit(id) })
            }
            reload()
          }}
        />
      )}
      {open?.kind === 'removeLot' && (
        <ConfirmDeleteDialog
          title={`Remove batch ${open.lot.id}?`}
          confirmLabel="Remove batch"
          busyLabel="Removing…"
          reasons={REMOVE_REASONS}
          requireReason
          message={
            <p>
              All {formatNumber(open.lot.quantityRemaining)} pieces of this batch ({formatMoney(open.lot.unitCost)} each,{' '}
              {location(open.lot.locationId)}) leave the stock and move to the trash, where they can be restored.
            </p>
          }
          onClose={() => setOpen(null)}
          lossReasons={LOSS_REASONS}
          onConfirm={async (reason, loss) => {
            const { id } = open.lot
            if (loss) {
              const writeOff = await api.writeOffLot(id, reason)
              setNotice({
                text: `Wrote off batch ${id} (${reason}): ${formatCost(writeOff.cost)} counted as a loss.`,
                undo: () => api.restoreLot(id),
              })
            } else {
              await api.deleteLot(id, reason)
              setNotice({ text: `Removed batch ${id} (${reason}).`, undo: () => api.restoreLot(id) })
            }
            reload()
          }}
        />
      )}
    </Layout>
  )
}

/** "Piece U-000001 · Serial number 3569…, Battery 91": its id and first filled-in details. */
function pieceSummary(unit: api.Unit, fields: api.CategoryField[]): string {
  const parts = fields
    .filter((f) => unit.attributes[f.key])
    .slice(0, 2)
    .map((f) => `${f.label} ${displayValue(f, unit.attributes[f.key])}`)
  return parts.length ? `Piece ${unit.id} · ${parts.join(', ')}` : `Piece ${unit.id}`
}
