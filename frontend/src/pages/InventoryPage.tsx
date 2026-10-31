import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { fetchProducts, fetchStock, type StockLevel } from '../api'
import { useIsOwner } from '../auth'
import { Layout } from '../components/Layout'

/** A stock level plus the product's SKU, which the stock endpoint does not return. */
type Row = StockLevel & { sku: string }

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: Row[] }

export function InventoryPage() {
  const isOwner = useIsOwner()
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [search, setSearch] = useState('')
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false // ignore the answer if the user left the page meanwhile
    // Load stock and products together, then look up each row's SKU by product id.
    Promise.all([fetchStock(), fetchProducts()])
      .then(([stock, products]) => {
        const skus = new Map(products.map((p) => [p.id, p.sku]))
        const rows = stock.map((s) => ({ ...s, sku: skus.get(s.productId) ?? '—' }))
        if (!cancelled) setState({ kind: 'ready', rows })
      })
      .catch((e: Error) => !cancelled && setState({ kind: 'error', message: e.message }))
    return () => {
      cancelled = true
    }
  }, [reloads])

  const reload = () => {
    setState({ kind: 'loading' })
    setReloads((n) => n + 1)
  }

  const rows = useMemo(() => {
    if (state.kind !== 'ready') return []
    const query = search.trim().toLowerCase()
    return state.rows
      .filter((r) => !query || `${r.sku} ${r.productName} ${r.locationName}`.toLowerCase().includes(query))
      .sort((a, b) => a.sku.localeCompare(b.sku) || a.locationName.localeCompare(b.locationName))
  }, [state, search])

  const totalPieces = rows.reduce((sum, r) => sum + r.quantity, 0)

  return (
    <Layout>
        <div className="page-head">
          <h1>Inventory</h1>
          <div className="head-tools">
            <input
              type="search"
              placeholder="Search SKU, product or location"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              aria-label="Search inventory"
            />
            {isOwner && (
              <Link className="button" to="/stock/new">
                Add stock
              </Link>
            )}
          </div>
        </div>

        {state.kind === 'loading' && <p className="muted">Loading stock…</p>}

        {state.kind === 'error' && (
          <div className="card error-box" role="alert">
            <p>{state.message}</p>
            <button onClick={reload}>Try again</button>
          </div>
        )}

        {state.kind === 'ready' && state.rows.length === 0 && (
          <div className="card empty">
            <p>No stock yet.</p>
            <p className="muted">Stock appears here once a purchase or a received unit or lot is recorded.</p>
            {isOwner && (
              <p>
                <Link className="button" to="/stock/new">
                  Add your first product and stock
                </Link>
              </p>
            )}
          </div>
        )}

        {state.kind === 'ready' && state.rows.length > 0 && (
          <div className="card table-wrap">
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Product</th>
                  <th>Location</th>
                  <th className="num">Quantity</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={`${r.productId}-${r.locationId}`}>
                    <td className="sku">{r.sku}</td>
                    <td>{r.productName}</td>
                    <td>{r.locationName}</td>
                    <td className="num">{r.quantity.toLocaleString()}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={4} className="muted">
                      Nothing matches “{search}”.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>
                    {rows.length} {rows.length === 1 ? 'row' : 'rows'}
                  </td>
                  <td className="num">{totalPieces.toLocaleString()} pcs</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
    </Layout>
  )
}
