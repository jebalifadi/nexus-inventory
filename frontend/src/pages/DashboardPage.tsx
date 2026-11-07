import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { formatDate, formatMoney } from '../catalog'
import { Layout } from '../components/Layout'

type Data = {
  positions: api.CurrencyPosition[]
  receivables: api.OpenBalance[]
  payables: api.OpenBalance[]
  sales: api.Sale[]
  stockPieces: number
}

async function load(): Promise<Data> {
  const [positions, receivables, payables, sales, stock] = await Promise.all([
    api.fetchOverview(),
    api.fetchReceivables(),
    api.fetchPayables(),
    api.fetchSales(),
    api.fetchStock(),
  ])
  return { positions, receivables, payables, sales, stockPieces: stock.reduce((sum, s) => sum + s.quantity, 0) }
}

/** Adds up money per currency: amounts in different currencies are never mixed. */
function totalsByCurrency(amounts: api.Money[]): api.Money[] {
  const minor = new Map<string, number>()
  for (const m of amounts) minor.set(m.currency, (minor.get(m.currency) ?? 0) + Math.round(Number(m.amount) * 1000))
  return [...minor].map(([currency, value]) => ({ currency, amount: String(value / 1000) }))
}

const isZero = (m: api.Money) => Number(m.amount) === 0

export function DashboardPage() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Read once when the page opens, so the figures do not shift between renders.
  const [now] = useState(() => new Date())

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const month = now.toLocaleDateString('sv').slice(0, 7)
  const monthSales = data?.sales.filter((s) => s.saleDate.startsWith(month)) ?? []
  const monthRevenue = totalsByCurrency(monthSales.map((s) => s.total))
  const monthProfit = totalsByCurrency(monthSales.flatMap((s) => (s.profit ? [s.profit] : [])))
  const unknownProfit = monthSales.filter((s) => !s.profit).length
  // Only currencies the business actually holds something in.
  const positions =
    data?.positions.filter((p) => ![p.cash, p.stockValue, p.receivables, p.payables].every(isZero)) ?? []

  return (
    <Layout>
      <div className="page-head">
        <h1>Dashboard</h1>
        <span className="muted">{now.toLocaleDateString(undefined, { dateStyle: 'full' })}</span>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data && (
        <>
          <div className="summary-grid">
            <div className="card section">
              <span className="eyebrow">Sales this month</span>
              <div className="big-number">
                <span>{monthSales.length}</span>
              </div>
              <span className="muted small">
                {monthRevenue.length ? monthRevenue.map(formatMoney).join(' + ') : 'Nothing sold yet this month'}
              </span>
            </div>
            <div className="card section">
              <span className="eyebrow">Profit this month</span>
              <div className="stack tight">
                {monthProfit.length === 0 ? (
                  <span className="big-number"><span>—</span></span>
                ) : (
                  monthProfit.map((m) => (
                    <span key={m.currency} className="big-number">
                      <span className={Number(m.amount) < 0 ? 'error' : undefined}>{formatMoney(m)}</span>
                    </span>
                  ))
                )}
              </div>
              {unknownProfit > 0 && (
                <span className="muted small">
                  {unknownProfit} sale{unknownProfit > 1 ? 's' : ''} not counted: an exchange rate is missing.{' '}
                  <Link to="/exchange-rates">Add one</Link>
                </span>
              )}
            </div>
            <div className="card section">
              <span className="eyebrow">Pieces in stock</span>
              <div className="big-number">
                <span>{data.stockPieces}</span>
              </div>
              <Link className="small" to="/inventory">See inventory →</Link>
            </div>
          </div>

          <section className="card table-card">
            <div className="card-head">
              <div className="stack tight">
                <h2>Your business, per currency</h2>
                <span className="muted small">
                  Business value = cash + stock + what customers owe you − what you owe suppliers.
                </span>
              </div>
              <Link className="small" to="/cash-book">Cash book →</Link>
            </div>
            {positions.length === 0 ? (
              <p className="pad muted">
                Nothing yet. Start with your opening balance on the <Link to="/capital">Capital</Link> page.
              </p>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Currency</th>
                      <th className="num">Cash</th>
                      <th className="num">Stock value</th>
                      <th className="num">Owed to you</th>
                      <th className="num">You owe</th>
                      <th className="num">Business value</th>
                    </tr>
                  </thead>
                  <tbody>
                    {positions.map((p) => (
                      <tr key={p.currency}>
                        <td className="strong">{p.currency}</td>
                        <td className={`num${Number(p.cash.amount) < 0 ? ' error' : ''}`}>{formatMoney(p.cash)}</td>
                        <td className="num">{formatMoney(p.stockValue)}</td>
                        <td className="num">{formatMoney(p.receivables)}</td>
                        <td className="num">{formatMoney(p.payables)}</td>
                        <td className="num strong">{formatMoney(p.businessValue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <div className="summary-grid">
            <OpenBalances
              title="Customers who owe you"
              empty="Nobody owes you money."
              rows={data.receivables}
              link="/sales"
            />
            <OpenBalances
              title="Suppliers you owe"
              empty="You owe no supplier."
              rows={data.payables}
              link="/purchases"
            />
          </div>
        </>
      )}
    </Layout>
  )
}

type OpenProps = { title: string; empty: string; rows: api.OpenBalance[]; link: string }

function OpenBalances({ title, empty, rows, link }: OpenProps) {
  const sorted = [...rows].sort((a, b) => b.daysOpen - a.daysOpen)
  return (
    <section className="card table-card">
      <div className="card-head center">
        <h2 className="h-sm">{title}</h2>
        <span className="muted small">{totalsByCurrency(rows.map((r) => r.balance)).map(formatMoney).join(' + ')}</span>
      </div>
      {rows.length === 0 ? (
        <p className="pad muted">{empty}</p>
      ) : (
        <div className="table-wrap">
          <table className="compact">
            <tbody>
              {sorted.map((r) => (
                <tr key={r.reference}>
                  <td>
                    <Link className="name-link" to={link}>
                      {r.counterparty ?? 'Walk-in'}
                    </Link>
                    <div className="muted small">
                      <span className="sku">{r.reference}</span> · {formatDate(r.date)}
                    </div>
                  </td>
                  <td className={`num small${r.daysOpen > 30 ? ' warning-text' : ' muted'}`}>{r.daysOpen} days</td>
                  <td className="num strong">{formatMoney(r.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
