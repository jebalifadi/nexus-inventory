import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { formatMoney } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'
import { NewSupplierDialog } from './NewPurchasePage'

type Data = {
  customers: api.Customer[]
  suppliers: api.Supplier[]
  receivables: api.OpenBalance[]
  payables: api.OpenBalance[]
}

async function load(): Promise<Data> {
  const [customers, suppliers, receivables, payables] = await Promise.all([
    api.fetchCustomers(),
    api.fetchSuppliers(),
    api.fetchReceivables(),
    api.fetchPayables(),
  ])
  return { customers, suppliers, receivables, payables }
}

/** What one person or company still owes (or is owed), per currency. */
function owed(rows: api.OpenBalance[], name: string): string {
  const totals = new Map<string, number>()
  for (const r of rows.filter((r) => r.counterparty === name)) {
    totals.set(r.balance.currency, (totals.get(r.balance.currency) ?? 0) + Number(r.balance.amount))
  }
  return [...totals].map(([currency, amount]) => formatMoney({ currency, amount: String(amount) })).join(' + ')
}

export function ContactsPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<'customers' | 'suppliers'>('customers')
  const [adding, setAdding] = useState(false)
  const [search, setSearch] = useState('')

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const q = search.trim().toLowerCase()
  const matches = (...values: (string | null)[]) => !q || values.some((v) => v?.toLowerCase().includes(q))
  const customers = data?.customers.filter((c) => matches(c.name, c.phone, c.email, c.country)) ?? []
  const suppliers = data?.suppliers.filter((s) => matches(s.name, s.country, s.contact)) ?? []

  return (
    <Layout>
      <div className="page-head">
        <h1>Contacts</h1>
        <div className="head-tools">
          <div className="segmented" role="group" aria-label="Show">
            <button aria-pressed={tab === 'customers'} onClick={() => setTab('customers')}>
              Customers {data && `(${data.customers.length})`}
            </button>
            <button aria-pressed={tab === 'suppliers'} onClick={() => setTab('suppliers')}>
              Suppliers {data && `(${data.suppliers.length})`}
            </button>
          </div>
          <input type="search" placeholder="Search…" value={search} onChange={(e) => setSearch(e.target.value)} />
          {isOwner && <button onClick={() => setAdding(true)}>{tab === 'customers' ? 'New customer' : 'New supplier'}</button>}
        </div>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data && tab === 'customers' && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Phone</th>
                <th>Email</th>
                <th>Country</th>
                <th className="num">Owes you</th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td className="strong">{c.name}</td>
                  <td>{c.phone ?? <span className="muted">—</span>}</td>
                  <td>{c.email ?? <span className="muted">—</span>}</td>
                  <td>{c.country ?? <span className="muted">—</span>}</td>
                  <td className="num">{owed(data.receivables, c.name) || <span className="muted">—</span>}</td>
                </tr>
              ))}
              {customers.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">{q ? 'No customer matches.' : 'No customers yet.'}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {data && tab === 'suppliers' && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Country</th>
                <th>Contact</th>
                <th>Notes</th>
                <th className="num">You owe</th>
              </tr>
            </thead>
            <tbody>
              {suppliers.map((s) => (
                <tr key={s.id}>
                  <td className="strong">{s.name}</td>
                  <td>{s.country ?? <span className="muted">—</span>}</td>
                  <td>{s.contact ?? <span className="muted">—</span>}</td>
                  <td>{s.notes ?? <span className="muted">—</span>}</td>
                  <td className="num">{owed(data.payables, s.name) || <span className="muted">—</span>}</td>
                </tr>
              ))}
              {suppliers.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">{q ? 'No supplier matches.' : 'No suppliers yet.'}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {adding && tab === 'suppliers' && (
        <NewSupplierDialog
          onClose={() => setAdding(false)}
          onCreated={(supplier) => setData((d) => d && { ...d, suppliers: [...d.suppliers, supplier] })}
        />
      )}
      {adding && tab === 'customers' && (
        <Dialog title="New customer" onClose={() => setAdding(false)}>
          <ActionForm
            submitLabel="Add customer"
            busyLabel="Saving…"
            onClose={() => setAdding(false)}
            onSubmit={async (form) => {
              const customer = await api.createCustomer({ name: String(form.get('name')), phone: String(form.get('phone')) })
              setData((d) => d && { ...d, customers: [...d.customers, customer] })
            }}
          >
            <label>
              Name
              <input name="name" required autoFocus />
            </label>
            <label>
              <span>Phone <span className="muted">(optional)</span></span>
              <input name="phone" type="tel" />
            </label>
          </ActionForm>
        </Dialog>
      )}
    </Layout>
  )
}
