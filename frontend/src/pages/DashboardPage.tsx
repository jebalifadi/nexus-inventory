import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { currencyDecimals, formatDate, formatMoney } from '../catalog'
import { Layout } from '../components/Layout'

type Data = {
  purchases: api.Purchase[]
  shipments: api.Shipment[]
  sales: api.Sale[]
  expenses: api.Expense[]
  positions: api.CurrencyPosition[]
  receivables: api.OpenBalance[]
  payables: api.OpenBalance[]
  stockPieces: number
}

async function load(): Promise<Data> {
  const [purchases, shipments, sales, expenses, positions, receivables, payables, stock] = await Promise.all([
    api.fetchPurchases(),
    api.fetchShipments(),
    api.fetchSales(),
    api.fetchExpenses(),
    api.fetchOverview(),
    api.fetchReceivables(),
    api.fetchPayables(),
    api.fetchStock(),
  ])
  const stockPieces = stock.reduce((sum, s) => sum + s.quantity, 0)
  return { purchases, shipments, sales, expenses, positions, receivables, payables, stockPieces }
}

type Period = 'month' | 'year' | 'all'
const PERIOD_LABELS: Record<Period, string> = { month: 'This month', year: 'This year', all: 'All time' }

/** Adds up amounts of one currency in its smallest unit (cents, millimes), so decimals do not drift. */
function sum(amounts: api.Money[], currency: string): number {
  const d = currencyDecimals(currency)
  return amounts.filter((m) => m.currency === currency).reduce((t, m) => t + Math.round(Number(m.amount) * 10 ** d), 0) / 10 ** d
}

/** Adds up money per currency: amounts in different currencies are never mixed. */
function totalsByCurrency(amounts: api.Money[]): api.Money[] {
  return [...new Set(amounts.map((m) => m.currency))].map((currency) => ({ currency, amount: String(sum(amounts, currency)) }))
}

export function DashboardPage() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Period>('month')
  const [chosenCurrency, setChosenCurrency] = useState<string | null>(null)
  // Read once when the page opens, so the figures do not shift between renders.
  const [now] = useState(() => new Date())

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const todayIso = now.toLocaleDateString('sv')
  const prefix = period === 'month' ? todayIso.slice(0, 7) : period === 'year' ? todayIso.slice(0, 4) : ''
  const inPeriod = (date: string) => date.startsWith(prefix)

  // Every currency the business has used, the one with most sales first (EUR before TND, usually).
  const used = data
    ? [...new Set([...data.sales.map((s) => s.currency), ...data.purchases.map((p) => p.currency), ...data.expenses.map((e) => e.amount.currency)])]
    : []
  const currency = chosenCurrency && used.includes(chosenCurrency) ? chosenCurrency : (used.includes('EUR') ? 'EUR' : used[0])

  const sales = data?.sales.filter((s) => inPeriod(s.saleDate)) ?? []
  const purchases = data?.purchases.filter((p) => inPeriod(p.purchaseDate)) ?? []
  const shipments = data?.shipments.filter((s) => inPeriod(s.shippedDate)) ?? []
  const expenses = data?.expenses.filter((e) => inPeriod(e.expenseDate)) ?? []
  // Costs on a piece are already in that piece's cost, and costs on a sale already lower its profit:
  // only general costs (rent, fees…) still have to come off the profit.
  const generalExpenses = expenses.filter((e) => !e.unitId && !e.saleId)

  const boughtFor = sum(purchases.map((p) => p.total), currency)
  const shipping = sum(shipments.map((s) => s.cost), currency)
  const invested = boughtFor + shipping
  const umsatz = sum(sales.map((s) => s.total), currency) - sum(sales.map((s) => s.returns), currency)
  const costs = sum(expenses.map((e) => e.amount), currency)
  const salesProfit = sum(sales.flatMap((s) => (s.profit ? [s.profit] : [])), currency)
  const profit = salesProfit - sum(generalExpenses.map((e) => e.amount), currency)
  const unknownProfit = sales.filter((s) => s.currency === currency && !s.profit).length
  const margin = umsatz > 0 ? Math.round((profit / umsatz) * 1000) / 10 : null
  const salesCount = sales.filter((s) => s.currency === currency).length
  const stockValue = data?.positions.find((p) => p.currency === currency)?.stockValue
  const money = (amount: number) => formatMoney({ amount: String(amount), currency })

  return (
    <Layout>
      <div className="page-head">
        <h1>Dashboard</h1>
        <div className="head-tools">
          <div className="segmented" role="group" aria-label="Period">
            {(Object.keys(PERIOD_LABELS) as Period[]).map((p) => (
              <button key={p} aria-pressed={period === p} onClick={() => setPeriod(p)}>
                {PERIOD_LABELS[p]}
              </button>
            ))}
          </div>
          {used.length > 1 && (
            <div className="segmented" role="group" aria-label="Currency">
              {used.map((c) => (
                <button key={c} aria-pressed={currency === c} onClick={() => setChosenCurrency(c)}>
                  {c}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data && !currency && (
        <div className="card empty">
          <strong>Nothing yet</strong>
          <span className="muted">
            Record your first <Link to="/purchases/new">purchase</Link> and the figures show up here.
          </span>
        </div>
      )}

      {data && currency && (
        <>
          <div className="summary-grid">
            <div className="card section">
              <span className="eyebrow">Money invested</span>
              <div className="big-number"><span>{money(invested)}</span></div>
              <span className="muted small">
                Goods {money(boughtFor)} · Shipping {money(shipping)}
              </span>
            </div>
            <div className="card section">
              <span className="eyebrow">Umsatz (revenue)</span>
              <div className="big-number"><span>{money(umsatz)}</span></div>
              <span className="muted small">
                {salesCount} sale{salesCount === 1 ? '' : 's'}
              </span>
            </div>
            <div className="card section">
              <span className="eyebrow">Costs</span>
              <div className="big-number"><span>{money(costs)}</span></div>
              <Link className="small" to="/expenses">Repairs, fees, rent… →</Link>
            </div>
            <div className="card section">
              <span className="eyebrow">Profit</span>
              <div className="big-number">
                <span className={profit < 0 ? 'error' : 'success'}>{money(profit)}</span>
              </div>
              <span className="muted small">
                {margin !== null ? `${margin}% of Umsatz` : 'No sales yet'}
                {unknownProfit > 0 && (
                  <>
                    {' · '}
                    <span className="warning-text">
                      {unknownProfit} sale{unknownProfit > 1 ? 's' : ''} not counted: <Link to="/exchange-rates">add a rate</Link>
                    </span>
                  </>
                )}
              </span>
            </div>
          </div>

          <div className="card section">
            <div className="spread">
              <span>
                <span className="eyebrow">In stock now</span>{' '}
                <strong>{data.stockPieces} pieces</strong>
                {stockValue && <span className="muted"> worth {formatMoney(stockValue)} at cost</span>}
              </span>
              <Link className="small" to="/inventory">See inventory →</Link>
            </div>
            <p className="muted small">
              Profit = what each piece sold for − its real cost (purchase + shipping + repairs on it) − costs of the
              sale − general costs. {PERIOD_LABELS[period]}{period !== 'all' ? `, by date` : ''}.
            </p>
          </div>

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
