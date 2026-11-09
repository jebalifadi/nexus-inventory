import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { formatDate, formatMoney, formatNumber, formatSigned } from '../catalog'
import { Layout } from '../components/Layout'
import { PeriodPicker } from '../components/PeriodPicker'
import { TrendChart } from '../components/TrendChart'
import { useI18n, useT } from '../i18n/context'
import { formatMonth, presetRange, todayIso, type Period, type Range } from '../period'

const TOP = 10
const startPeriod = (): Period => ({ ...presetRange('last6m'), preset: 'last6m', compare: false })

type Data = { trend: api.MonthlyTrend; sales: api.SalesAnalytics; slow: api.SlowStock }

function load(range: Range, currency: string): Promise<Data> {
  return Promise.all([
    api.fetchTrend(range, currency),
    api.fetchSalesAnalytics(range, currency),
    api.fetchSlowStock(currency),
  ]).then(([trend, sales, slow]) => ({ trend, sales, slow }))
}

function missingRates(data: Data): api.MissingRate[] {
  const earliest = new Map<string, api.MissingRate>()
  for (const rate of [...data.trend.missingRates, ...data.sales.missingRates, ...data.slow.missingRates]) {
    const seen = earliest.get(rate.from)
    if (!seen || rate.neededBy < seen.neededBy) earliest.set(rate.from, rate)
  }
  return [...earliest.values()]
}

const profitClass = (money: api.Money) => (Number(money.amount) < 0 ? 'loss' : Number(money.amount) > 0 ? 'profit' : undefined)

function Profit({ money, unknown }: { money: api.Money; unknown: boolean }) {
  const t = useT()
  return (
    <>
      <span className={profitClass(money)}>{formatSigned(money)}</span>
      {unknown && (
        <span className="warning-text" title={t('ana.unknownProfit')}>
          {' *'}
          <span className="sr-only">{t('ana.unknownProfit')}</span>
        </span>
      )}
    </>
  )
}

function Panel({ title, note, empty, children }: { title: string; note?: string; empty: string | null; children: ReactNode }) {
  return (
    <section className="card table-card">
      <div className="card-head">
        <div className="stack tight">
          <h2 className="h-sm">{title}</h2>
          {note && <span className="muted small">{note}</span>}
        </div>
      </div>
      {empty ? <p className="pad muted">{empty}</p> : <div className="table-wrap">{children}</div>}
    </section>
  )
}

const productName = (p: { name: string | null; sku: string | null; productId: number }) => p.name ?? p.sku ?? `#${p.productId}`

function ProductCell({ product }: { product: { name: string | null; sku: string | null; productId: number } }) {
  return (
    <td>
      <Link className="name-link" to={`/products/${product.productId}`}>{productName(product)}</Link>
      {product.sku && product.sku !== product.name && <div className="muted small sku">{product.sku}</div>}
    </td>
  )
}

export function AnalyticsPage() {
  const t = useT()
  const currency = useI18n().preferences.currency || 'EUR'
  const [chosen, setChosen] = useState<Period>(startPeriod)
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)

  const period: Period = chosen.preset ? { ...chosen, ...presetRange(chosen.preset, todayIso()) } : chosen
  const query = JSON.stringify([{ from: period.from, to: period.to }, currency])

  useEffect(() => {
    let alive = true
    const [range, inCurrency] = JSON.parse(query) as [Range, string]
    load(range, inCurrency)
      .then((loaded) => {
        if (!alive) return
        setData(loaded)
        setError(null)
      })
      .catch((e: Error) => {
        if (!alive) return
        setData(null)
        setError(e.message)
      })
    return () => {
      alive = false
    }
  }, [query])

  const missing = data ? missingRates(data) : []
  const products = data?.sales.products ?? []
  const bestSellers = products.filter((p) => p.quantity > 0).slice(0, TOP)
  const mostProfit = [...products].sort((a, b) => Number(b.profit.amount) - Number(a.profit.amount)).slice(0, TOP)
  const hasMonths = data?.trend.months.some((m) => m.salesCount > 0 || Number(m.profit.amount) !== 0) ?? false

  return (
    <Layout>
      <div className="page-head">
        <h1>{t('ana.title')}</h1>
        <div className="head-tools">
          <PeriodPicker value={period} onChange={setChosen} allowCompare={false} />
          <Link className="small" to="/settings">{t('dash.currency')}: {currency}</Link>
        </div>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">{t('common.loading')}</p>}

      {data && (
        <div className="stack">
          {missing.length > 0 && (
            <div className="card section" role="status">
              <span className="warning-text">{t('dash.ratesMissing', { currency })}</span>
              {missing.map((rate) => (
                <Link key={rate.from} className="small" to="/exchange-rates">
                  {t('dash.rateNeeded', { from: rate.from, to: rate.to, date: formatDate(rate.neededBy) })}
                </Link>
              ))}
            </div>
          )}

          <section className="card section">
            <h2 className="h-sm">{t('ana.trend')}</h2>
            {hasMonths ? (
              <>
                <TrendChart months={data.trend.months} />
                <details>
                  <summary className="small">{t('ana.tableView')}</summary>
                  <div className="table-wrap">
                    <table className="compact">
                      <thead>
                        <tr>
                          <th>{t('ana.month')}</th>
                          <th className="num">{t('dash.revenue')}</th>
                          <th className="num">{t('dash.profit')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.trend.months.map((m) => (
                          <tr key={m.month}>
                            <td>{formatMonth(m.month)}</td>
                            <td className="num">{formatMoney(m.revenue)}</td>
                            <td className="num"><span className={profitClass(m.profit)}>{formatSigned(m.profit)}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              </>
            ) : (
              <p className="muted">{t('ana.noSales')}</p>
            )}
          </section>

          <div className="summary-grid">
            <Panel title={t('ana.bestSellers')} empty={bestSellers.length ? null : t('ana.noSales')}>
              <table className="compact">
                <thead>
                  <tr>
                    <th>{t('ana.product')}</th>
                    <th className="num">{t('ana.sold')}</th>
                    <th className="num">{t('dash.revenue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {bestSellers.map((p) => (
                    <tr key={p.productId}>
                      <ProductCell product={p} />
                      <td className="num strong">{formatNumber(p.quantity)}</td>
                      <td className="num">{formatMoney(p.revenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>

            <Panel title={t('ana.profitByProduct')} note={t('ana.profitNote')} empty={mostProfit.length ? null : t('ana.noSales')}>
              <table className="compact">
                <thead>
                  <tr>
                    <th>{t('ana.product')}</th>
                    <th className="num">{t('dash.profit')}</th>
                  </tr>
                </thead>
                <tbody>
                  {mostProfit.map((p) => (
                    <tr key={p.productId}>
                      <ProductCell product={p} />
                      <td className="num strong"><Profit money={p.profit} unknown={p.unknownProfit} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>

            <Panel title={t('ana.profitByCategory')} empty={data.sales.categories.length ? null : t('ana.noSales')}>
              <table className="compact">
                <thead>
                  <tr>
                    <th>{t('ana.category')}</th>
                    <th className="num">{t('ana.sold')}</th>
                    <th className="num">{t('dash.revenue')}</th>
                    <th className="num">{t('dash.profit')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.sales.categories.map((c) => (
                    <tr key={c.categoryName ?? ''}>
                      <td>{c.categoryName ?? '—'}</td>
                      <td className="num">{formatNumber(c.quantity)}</td>
                      <td className="num">{formatMoney(c.revenue)}</td>
                      <td className="num strong"><Profit money={c.profit} unknown={c.unknownProfit} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>

            <Panel
              title={t('ana.slowStock')}
              note={t('ana.slowNote', { days: data.slow.days })}
              empty={data.slow.products.length ? null : t('ana.noSlow')}
            >
              <table className="compact">
                <thead>
                  <tr>
                    <th>{t('ana.product')}</th>
                    <th className="num">{t('ana.pieces')}</th>
                    <th className="num">{t('ana.waiting')}</th>
                    <th className="num">{t('ana.value')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.slow.products.map((p) => (
                    <tr key={p.productId}>
                      <ProductCell product={p} />
                      <td className="num">{formatNumber(p.quantity)}</td>
                      <td className="num warning-text">{t('dash.days', { n: p.daysInStock })}</td>
                      <td className="num strong">{formatMoney(p.value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Panel>
          </div>
        </div>
      )}
    </Layout>
  )
}
