import { useEffect, useMemo, useState } from 'react'
import { fetchStock, type StockLevel } from '../api'
import { useAuth } from '../auth'

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: StockLevel[] }

export function InventoryPage() {
  const { session, logout } = useAuth()
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [search, setSearch] = useState('')
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let cancelled = false // ignore the answer if the user left the page meanwhile
    fetchStock()
      .then((rows) => !cancelled && setState({ kind: 'ready', rows }))
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
      .filter((r) => !query || `${r.productName} ${r.locationName}`.toLowerCase().includes(query))
      .sort((a, b) => a.productName.localeCompare(b.productName) || a.locationName.localeCompare(b.locationName))
  }, [state, search])

  const totalPieces = rows.reduce((sum, r) => sum + r.quantity, 0)

  return (
    <>
      <header className="topbar">
        <span className="brand">Nexus</span>
        <span className="muted">
          {session?.username} · {session?.roles.join(', ').toLowerCase()}
        </span>
        <button className="secondary" onClick={logout}>
          Log out
        </button>
      </header>

      <main className="page">
        <div className="page-head">
          <h1>Inventory</h1>
          <input
            type="search"
            placeholder="Search product or location"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search inventory"
          />
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
          </div>
        )}

        {state.kind === 'ready' && state.rows.length > 0 && (
          <div className="card table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Location</th>
                  <th className="num">Quantity</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={`${r.productId}-${r.locationId}`}>
                    <td>{r.productName}</td>
                    <td>{r.locationName}</td>
                    <td className="num">{r.quantity.toLocaleString()}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={3} className="muted">
                      Nothing matches “{search}”.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={2}>
                    {rows.length} {rows.length === 1 ? 'row' : 'rows'}
                  </td>
                  <td className="num">{totalPieces.toLocaleString()} pcs</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
      </main>
    </>
  )
}
