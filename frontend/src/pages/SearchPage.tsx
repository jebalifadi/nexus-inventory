import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as api from '../api'
import { formatDate, formatMoney, STATUS_LABELS } from '../catalog'
import { Layout } from '../components/Layout'

type Data = {
  products: api.Product[]
  units: api.Unit[]
  sales: api.Sale[]
  purchases: api.Purchase[]
  customers: api.Customer[]
  suppliers: api.Supplier[]
}

type Result = { key: string; to: string; title: string; detail: string }

const MAX_PER_GROUP = 20

/** The top bar's search: products, pieces (IMEI, serial…), sales, purchases, customers and suppliers. */
export function SearchPage() {
  const [params] = useSearchParams()
  const query = (params.get('q') ?? '').trim()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      api.fetchProducts(),
      api.fetchUnits(),
      api.fetchSales(),
      api.fetchPurchases(),
      api.fetchCustomers(),
      api.fetchSuppliers(),
    ])
      .then(([products, units, sales, purchases, customers, suppliers]) =>
        setData({ products, units, sales, purchases, customers, suppliers }),
      )
      .catch((e: Error) => setError(e.message))
  }, [])

  const q = query.toLowerCase()
  const hit = (...values: (string | null | undefined)[]) => values.some((v) => v?.toLowerCase().includes(q))
  const productName = (id: number) => data?.products.find((p) => p.id === id)?.name ?? `Product ${id}`

  const groups: { title: string; results: Result[] }[] = data && q
    ? [
        {
          title: 'Products',
          results: data.products
            .filter((p) => hit(p.sku, p.name, ...Object.values(p.attributes)))
            .map((p) => ({ key: `p${p.id}`, to: `/products/${p.id}`, title: p.name, detail: p.sku })),
        },
        {
          title: 'Pieces',
          results: data.units
            .filter((u) => hit(u.id, ...Object.values(u.attributes)))
            .map((u) => ({
              key: `u${u.id}`,
              to: `/products/${u.productId}`,
              title: `${u.id} · ${productName(u.productId)}`,
              detail: [...Object.values(u.attributes).filter(Boolean), STATUS_LABELS[u.status]].join(' · '),
            })),
        },
        {
          title: 'Sales',
          results: data.sales
            .filter((s) => hit(s.id, s.customerName, s.notes, ...s.lines.map((l) => l.unitId)))
            .map((s) => ({
              key: `s${s.id}`,
              to: '/sales',
              title: `${s.id} · ${s.customerName ?? 'Walk-in'}`,
              detail: `${formatDate(s.saleDate)} · ${formatMoney(s.total)}`,
            })),
        },
        {
          title: 'Purchases',
          results: data.purchases
            .filter((p) => hit(p.id, p.supplierName, p.notes, ...p.lines.flatMap((l) => [...l.unitIds, l.lotId])))
            .map((p) => ({
              key: `b${p.id}`,
              to: '/purchases',
              title: `${p.id} · ${p.supplierName}`,
              detail: `${formatDate(p.purchaseDate)} · ${formatMoney(p.total)}`,
            })),
        },
        {
          title: 'Customers',
          results: data.customers
            .filter((c) => hit(c.name, c.phone, c.email))
            .map((c) => ({ key: `c${c.id}`, to: '/customers', title: c.name, detail: c.phone ?? c.email ?? '' })),
        },
        {
          title: 'Suppliers',
          results: data.suppliers
            .filter((s) => hit(s.name, s.contact, s.country))
            .map((s) => ({ key: `l${s.id}`, to: '/suppliers', title: s.name, detail: s.country ?? '' })),
        },
      ].filter((g) => g.results.length > 0)
    : []

  return (
    <Layout>
      <div className="page-head">
        <h1>{query ? `Results for “${query}”` : 'Search'}</h1>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Searching…</p>}
      {data && !q && <p className="muted">Type a product, IMEI, sale number or customer in the search box above.</p>}
      {data && q && groups.length === 0 && (
        <div className="card empty">
          <strong>Nothing found</strong>
          <span className="muted">Check the spelling, or try part of the name or number.</span>
        </div>
      )}

      {groups.map((group) => (
        <section key={group.title} className="card table-card">
          <div className="card-head center">
            <h2 className="h-sm">{group.title}</h2>
            <span className="badge">{group.results.length}</span>
          </div>
          <ul className="result-list">
            {group.results.slice(0, MAX_PER_GROUP).map((r) => (
              <li key={r.key}>
                <Link to={r.to}>
                  <span className="strong">{r.title}</span>
                  <span className="muted small">{r.detail}</span>
                </Link>
              </li>
            ))}
          </ul>
          {group.results.length > MAX_PER_GROUP && (
            <p className="pad muted small">Showing the first {MAX_PER_GROUP}. Type more to narrow it down.</p>
          )}
        </section>
      ))}
    </Layout>
  )
}
