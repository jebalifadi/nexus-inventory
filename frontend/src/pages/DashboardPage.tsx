import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { currencyDecimals, getDisplayLocale, formatDate, formatMoney, formatSigned } from '../catalog'
import { Layout } from '../components/Layout'
import { defaultCurrency, useT } from '../i18n/context'
import { PeriodPicker } from '../components/PeriodPicker'
import { defaultPeriod, inRange, presetRange, previousRange, todayIso, type Period, type Range } from '../period'

type Data = {
  purchases: api.Purchase[]
  shipments: api.Shipment[]
  sales: api.Sale[]
  expenses: api.Expense[]
  writeOffs: api.WriteOff[]
  positions: api.CurrencyPosition[]
  receivables: api.OpenBalance[]
  payables: api.OpenBalance[]
  stockPieces: number
}

/** How often the figures are fetched again while the page stays open. */
const REFRESH_EVERY_MS = 5 * 60 * 1000

async function load(): Promise<Data> {
  const [purchases, shipments, sales, expenses, writeOffs, positions, receivables, payables, stock] = await Promise.all([
    api.fetchPurchases(),
    api.fetchShipments(),
    api.fetchSales(),
    api.fetchExpenses(),
    api.fetchWriteOffs(),
    api.fetchOverview(),
    api.fetchReceivables(),
    api.fetchPayables(),
    api.fetchStock(),
  ])
  const stockPieces = stock.reduce((sum, s) => sum + s.quantity, 0)
  return { purchases, shipments, sales, expenses, writeOffs, positions, receivables, payables, stockPieces }
}


/** Adds up amounts of one currency in its smallest unit (cents, millimes), so decimals do not drift. */
function sum(amounts: api.Money[], currency: string): number {
  const d = currencyDecimals(currency)
  return amounts.filter((m) => m.currency === currency).reduce((t, m) => t + Math.round(Number(m.amount) * 10 ** d), 0) / 10 ** d
}

/** Adds up money per currency: amounts in different currencies are never mixed. */
function totalsByCurrency(amounts: api.Money[]): api.Money[] {
  return [...new Set(amounts.map((m) => m.currency))].map((currency) => ({ currency, amount: String(sum(amounts, currency)) }))
}

type Figures = {
  boughtFor: number
  shipping: number
  invested: number
  revenue: number
  costs: number
  expenses: number
  losses: number
  profit: number
  salesCount: number
  unknownProfit: number
}

/** The dashboard figures for one currency and one date range. */
function figures(data: Data, currency: string, range: Range): Figures {
  const within = (date: string) => inRange(date, range)
  const sales = data.sales.filter((s) => within(s.saleDate))
  const expenses = data.expenses.filter((e) => within(e.expenseDate))
  // Costs on a piece are already in that piece's cost, and costs on a sale already lower its profit:
  // only general costs (rent, fees…) still have to come off the profit.
  const general = expenses.filter((e) => !e.unitId && !e.saleId)
  const boughtFor = sum(data.purchases.filter((p) => within(p.purchaseDate)).map((p) => p.total), currency)
  const shipping = sum(data.shipments.filter((s) => within(s.shippedDate)).map((s) => s.cost), currency)
  const salesProfit = sum(sales.flatMap((s) => (s.profit ? [s.profit] : [])), currency)
  // Damaged or lost goods: their real cost is a loss. It lowers profit, not cash.
  const losses = sum(data.writeOffs.filter((w) => within(w.date)).flatMap((w) => w.cost), currency)
  const spent = sum(expenses.map((e) => e.amount), currency)
  return {
    boughtFor,
    shipping,
    invested: boughtFor + shipping,
    revenue: sum(sales.map((s) => s.total), currency) - sum(sales.map((s) => s.returns), currency),
    costs: spent + losses,
    expenses: spent,
    losses,
    profit: salesProfit - sum(general.map((e) => e.amount), currency) - losses,
    salesCount: sales.filter((s) => s.currency === currency).length,
    unknownProfit: sales.filter((s) => s.currency === currency && !s.profit).length,
  }
}

export function DashboardPage() {
  const t = useT()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [chosen, setChosen] = useState<Period>(defaultPeriod)
  const [chosenCurrency, setChosenCurrency] = useState<string | null>(null)
  const [today, setToday] = useState(todayIso)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)

  // Loads now, then keeps the page current without a reload:
  // - every few minutes, and when you come back to the tab: new sales and purchases appear;
  // - just after midnight: "today" moves on, so This month, Last 7 days… follow the calendar.
  // A failed background refresh keeps the figures already on screen.
  useEffect(() => {
    let alive = true
    const refresh = (first = false) => {
      setToday(todayIso())
      load()
        .then((loaded) => {
          if (!alive) return
          setData(loaded)
          setError(null)
          setUpdatedAt(new Date())
        })
        .catch((e: Error) => {
          if (alive && first) setError(e.message)
        })
    }
    refresh(true)
    const interval = window.setInterval(() => refresh(), REFRESH_EVERY_MS)
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    let midnight: number | undefined
    const scheduleMidnight = () => {
      const now = new Date()
      const next = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 5)
      midnight = window.setTimeout(() => {
        refresh()
        scheduleMidnight()
      }, next.getTime() - now.getTime())
    }
    scheduleMidnight()
    return () => {
      alive = false
      window.clearInterval(interval)
      window.clearTimeout(midnight)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  // A preset is worked out from today each time, so it moves with the calendar; a dragged range stays put.
  const period: Period = chosen.preset ? { ...chosen, ...presetRange(chosen.preset, today) } : chosen

  // Every currency the business has used, the one with most sales first (EUR before TND, usually).
  const used = data
    ? [...new Set([...data.sales.map((s) => s.currency), ...data.purchases.map((p) => p.currency), ...data.expenses.map((e) => e.amount.currency)])]
    : []
  const currency = chosenCurrency && used.includes(chosenCurrency) ? chosenCurrency : defaultCurrency(used)

  const empty: Figures = { boughtFor: 0, shipping: 0, invested: 0, revenue: 0, costs: 0, expenses: 0, losses: 0, profit: 0, salesCount: 0, unknownProfit: 0 }
  const now = data && currency ? figures(data, currency, period) : empty
  const previous = previousRange(period)
  const before = period.compare && previous && data && currency ? figures(data, currency, previous) : null
  const { boughtFor, shipping, invested, profit, salesCount, unknownProfit } = now
  const umsatz = now.revenue
  const costs = now.costs
  const margin = umsatz > 0 ? Math.round((profit / umsatz) * 1000) / 10 : null
  const stockValue = data?.positions.find((p) => p.currency === currency)?.stockValue
  const money = (amount: number) => formatMoney({ amount: String(amount), currency })

  return (
    <Layout>
      <div className="page-head">
        <div className="stack tight">
          <h1>{t('dash.title')}</h1>
          {updatedAt && (
            <span className="muted small">
              {t('dash.updated', { time: updatedAt.toLocaleTimeString(getDisplayLocale(), { hour: '2-digit', minute: '2-digit' }) })}
            </span>
          )}
        </div>
        <div className="head-tools">
          <PeriodPicker value={period} onChange={setChosen} />
          {used.length > 1 && (
            <div className="segmented" role="group" aria-label={t('dash.currency')}>
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
      {!data && !error && <p className="muted">{t('common.loading')}</p>}

      {data && !currency && (
        <div className="card empty">
          <strong>{t('dash.nothingTitle')}</strong>
          <span className="muted">
            <Link to="/purchases/new">{t('dash.nothingText')}</Link>
          </span>
        </div>
      )}

      {data && currency && (
        <>
          <div className="summary-grid">
            <div className="card section">
              <span className="eyebrow">{t('dash.invested')}</span>
              <div className="big-number"><span>{money(invested)}</span></div>
              <span className="muted small">
                {t('dash.investedSplit', { goods: money(boughtFor), shipping: money(shipping) })}
              </span>
              {before && <Delta now={invested} before={before.invested} tone="neutral" format={money} />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.revenue')}</span>
              <div className="big-number"><span>{money(umsatz)}</span></div>
              <span className="muted small">
                {salesCount === 1 ? t('dash.salesCountOne') : t('dash.salesCount', { n: salesCount })}
              </span>
              {before && <Delta now={umsatz} before={before.revenue} tone="up-good" format={money} />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.costs')}</span>
              <div className="big-number"><span>{money(costs)}</span></div>
              {now.losses > 0 && (
                <span className="muted small">
                  {t('dash.costsSplit', { expenses: money(now.expenses), losses: money(now.losses) })}
                </span>
              )}
              <Link className="small" to="/expenses">{t('dash.costsLink')}</Link>
              {before && <Delta now={costs} before={before.costs} tone="up-bad" format={money} />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.profit')}</span>
              <div className="big-number">
                <span className={profit < 0 ? 'loss' : profit > 0 ? 'profit' : undefined}>{formatSigned({ amount: String(profit), currency })}</span>
              </div>
              <span className="muted small">
                {margin !== null ? t('dash.margin', { p: margin }) : t('dash.noSales')}
                {unknownProfit > 0 && (
                  <>
                    {' · '}
                    <span className="warning-text">
                      {t('dash.notCounted', { n: unknownProfit })} <Link to="/exchange-rates">{t('dash.addRate')}</Link>
                    </span>
              {before && <Delta now={profit} before={before.profit} tone="up-good" format={money} />}
                  </>
                )}
              </span>
            </div>
          </div>

          <div className="card section">
            <div className="spread">
              <span>
                <span className="eyebrow">{t('dash.inStock')}</span>{' '}
                <strong>{t('dash.pieces', { n: data.stockPieces })}</strong>
                {stockValue && <span className="muted"> {t('dash.worth', { value: formatMoney(stockValue) })}</span>}
              </span>
              <Link className="small" to="/inventory">{t('dash.seeInventory')}</Link>
            </div>
            <p className="muted small">
              {t('dash.explain')}
            </p>
          </div>

          <div className="summary-grid">
            <OpenBalances
              title={t('dash.owedToYou')}
              empty={t('dash.nobodyOwes')}
              rows={data.receivables}
              link="/sales"
            />
            <OpenBalances
              title={t('dash.youOwe')}
              empty={t('dash.youOweNone')}
              rows={data.payables}
              link="/purchases"
            />
          </div>
        </>
      )}
    </Layout>
  )
}

type DeltaProps = { now: number; before: number; tone: 'up-good' | 'up-bad' | 'neutral'; format: (n: number) => string }

/** "▲ 12% vs previous period", green when the change is good for the business, red when bad. */
function Delta({ now, before, tone, format }: DeltaProps) {
  const t = useT()
  const change = now - before
  const percent = before !== 0 ? Math.round((change / Math.abs(before)) * 1000) / 10 : null
  const mood = change === 0 || tone === 'neutral' ? 'neutral' : (change > 0) === (tone === 'up-good') ? 'good' : 'bad'
  const arrow = change > 0 ? '▲' : change < 0 ? '▼' : '='
  const amount = percent !== null ? `${Math.abs(percent)}%` : change === 0 ? '0%' : t('dash.newVsPrev')
  return (
    <span className={`delta ${mood}`} title={t('dash.prevWas', { value: format(before) })}>
      <span aria-hidden="true">{arrow}</span>
      <span>
        {change < 0 ? '−' : change > 0 ? '+' : ''}
        {amount}
      </span>
      <span className="muted">{t('dash.vsPrev')}</span>
      <span className="sr-only">{t('dash.prevWas', { value: format(before) })}</span>
    </span>
  )
}

type OpenProps = { title: string; empty: string; rows: api.OpenBalance[]; link: string }

function OpenBalances({ title, empty, rows, link }: OpenProps) {
  const t = useT()
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
                      {r.counterparty ?? t('common.walkIn')}
                    </Link>
                    <div className="muted small">
                      <span className="sku">{r.reference}</span> · {formatDate(r.date)}
                    </div>
                  </td>
                  <td className={`num small${r.daysOpen > 30 ? ' warning-text' : ' muted'}`}>{t('dash.days', { n: r.daysOpen })}</td>
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
