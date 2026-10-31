import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { columnFields, displayValue } from '../catalog'
import { Layout } from '../components/Layout'

type Data = {
  stock: api.StockLevel[]
  products: Map<number, api.Product>
  categories: api.Category[]
  locations: api.Location[]
}

type State = { kind: 'loading' } | { kind: 'error'; message: string } | { kind: 'ready'; data: Data }

/** A stock row with its product, so the SKU and the product's fields can be shown. */
type Row = api.StockLevel & { product: api.Product }

export function InventoryPage() {
  const isOwner = useIsOwner()
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [search, setSearch] = useState('')
  const [locationId, setLocationId] = useState<number | 'all'>('all')
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false // ignore the answer if the user left the page meanwhile
    Promise.all([api.fetchStock(), api.fetchProducts(), api.fetchCategories(), api.fetchLocations()])
      .then(([stock, products, categories, locations]) => {
        const data = { stock, products: new Map(products.map((p) => [p.id, p])), categories, locations }
        if (!cancelled) setState({ kind: 'ready', data })
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

  // One group per category, each with its own columns, filtered by location and search.
  const groups = useMemo(() => {
    if (state.kind !== 'ready') return []
    const { stock, products, categories } = state.data
    const query = search.trim().toLowerCase()
    const rows: Row[] = stock
      .filter((s) => products.has(s.productId))
      .map((s) => ({ ...s, product: products.get(s.productId)! }))
      .filter((r) => locationId === 'all' || r.locationId === locationId)
      .filter(
        (r) =>
          !query ||
          `${r.product.sku} ${r.product.name} ${r.locationName} ${Object.values(r.product.attributes).join(' ')}`
            .toLowerCase()
            .includes(query),
      )
    return categories
      .map((category) => ({
        category,
        columns: columnFields(category),
        rows: rows
          .filter((r) => r.product.categoryId === category.id)
          .sort((a, b) => a.product.name.localeCompare(b.product.name) || a.locationName.localeCompare(b.locationName)),
      }))
      .filter((g) => g.rows.length > 0)
  }, [state, search, locationId])

  const hasStock = state.kind === 'ready' && state.data.stock.length > 0

  return (
    <Layout>
      <div className="page-head">
        <h1>Inventory</h1>
        <div className="head-tools">
          {state.kind === 'ready' && state.data.locations.length > 1 && (
            <select
              aria-label="Location"
              value={locationId}
              onChange={(e) => setLocationId(e.target.value === 'all' ? 'all' : Number(e.target.value))}
            >
              <option value="all">All locations</option>
              {state.data.locations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          )}
          <input
            type="search"
            placeholder="Search SKU, name or location"
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

      {state.kind === 'ready' && !hasStock && (
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

      {hasStock && groups.length === 0 && <p className="muted">Nothing matches your search.</p>}

      {groups.map(({ category, columns, rows }) => {
        const total = rows.reduce((sum, r) => sum + r.quantity, 0)
        return (
          <section className="card table-card" key={category.id}>
            <div className="card-head">
              <div className="title-row">
                <h2>{category.name}</h2>
                <span className="muted small">
                  {total.toLocaleString()} {total === 1 ? 'piece' : 'pieces'}
                </span>
              </div>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>Name</th>
                    {columns.map((f) => (
                      <th key={f.key}>{f.label}</th>
                    ))}
                    <th>Location</th>
                    <th className="num">In stock</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={`${r.productId}-${r.locationId}`}>
                      <td className="sku">{r.product.sku}</td>
                      <td>
                        <Link className="name-link" to={`/products/${r.productId}`}>
                          {r.product.name}
                        </Link>
                      </td>
                      {columns.map((f) => (
                        <td key={f.key}>{displayValue(f, r.product.attributes[f.key])}</td>
                      ))}
                      <td className="muted-strong">{r.locationName}</td>
                      <td className="num strong">{r.quantity.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )
      })}
    </Layout>
  )
}
