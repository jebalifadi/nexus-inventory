import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { getDisplayLocale, formatDate, formatMoney, formatSigned } from '../catalog'
import { Layout } from '../components/Layout'
import { useI18n, useT } from '../i18n/context'
import { PeriodPicker } from '../components/PeriodPicker'
import { defaultPeriod, presetRange, previousRange, todayIso, type Period, type Range } from '../period'

type Data = {
  now: api.ReportSummary
  /** The period to compare with, when comparing is on. */
  before: api.ReportSummary | null
  position: api.ReportPosition
  stockPieces: number
}

/** How often the figures are fetched again while the page stays open. */
const REFRESH_EVERY_MS = 5 * 60 * 1000

async function load(range: Range, previous: Range | null, currency: string): Promise<Data> {
  const [now, before, position, stock] = await Promise.all([
    api.fetchReportSummary(range, currency),
    previous ? api.fetchReportSummary(previous, currency) : null,
    api.fetchReportPosition(currency),
    api.fetchStock(),
  ])
  const stockPieces = stock.reduce((sum, s) => sum + s.quantity, 0)
  return { now, before, position, stockPieces }
}

/** One line per missing rate, naming the earliest date any of the figures needs. */
function missingRates(data: Data): api.MissingRate[] {
  const earliest = new Map<string, api.MissingRate>()
  for (const rate of [...data.now.missingRates, ...(data.before?.missingRates ?? []), ...data.position.missingRates]) {
    const seen = earliest.get(rate.from)
    if (!seen || rate.neededBy < seen.neededBy) earliest.set(rate.from, rate)
  }
  return [...earliest.values()]
}

export function DashboardPage() {
  const t = useT()
  // The currency from Settings: the backend converts every figure into it.
  const currency = useI18n().preferences.currency || 'EUR'
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [chosen, setChosen] = useState<Period>(defaultPeriod)
  const [today, setToday] = useState(todayIso)
  const [refreshes, setRefreshes] = useState(0)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const loadedQuery = useRef<string | null>(null)

  // Keeps the page current without a reload:
  // - every few minutes, and when you come back to the tab: new sales and purchases appear;
  // - just after midnight: "today" moves on, so This month, Last 7 days… follow the calendar.
  useEffect(() => {
    const refresh = () => {
      setToday(todayIso())
      setRefreshes((n) => n + 1)
    }
    const interval = window.setInterval(refresh, REFRESH_EVERY_MS)
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
      window.clearInterval(interval)
      window.clearTimeout(midnight)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  // A preset is worked out from today each time, so it moves with the calendar; a dragged range stays put.
  const period: Period = chosen.preset ? { ...chosen, ...presetRange(chosen.preset, today) } : chosen
  const comparison = period.compare ? previousRange(period) : null
  const query = JSON.stringify([{ from: period.from, to: period.to }, comparison, currency])

  useEffect(() => {
    let alive = true
    const [range, previous, inCurrency] = JSON.parse(query) as [Range, Range | null, string]
    load(range, previous, inCurrency)
      .then((loaded) => {
        if (!alive) return
        loadedQuery.current = query
        setData(loaded)
        setError(null)
        setUpdatedAt(new Date())
      })
      .catch((e: Error) => {
        if (!alive) return
        // A failed background refresh keeps the figures on screen, but figures of another period would mislead.
        if (loadedQuery.current !== query) {
          setData(null)
          setError(e.message)
        }
      })
    return () => {
      alive = false
    }
  }, [query, refreshes])

  const figures = data?.now
  const before = data?.before
  const missing = data ? missingRates(data) : []
  const profitAmount = Number(figures?.profit.amount ?? 0)
  const profitClass = profitAmount < 0 ? 'loss' : profitAmount > 0 ? 'profit' : undefined

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
          <Link className="small" to="/settings">{t('dash.currency')}: {currency}</Link>
        </div>
      </div>

      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">{t('common.loading')}</p>}

      {data && !data.now.hasActivity && (
        <div className="card empty">
          <strong>{t('dash.nothingTitle')}</strong>
          <span className="muted">
            <Link to="/purchases/new">{t('dash.nothingText')}</Link>
          </span>
        </div>
      )}

      {data && figures?.hasActivity && (
        <>
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
          <div className="summary-grid">
            <div className="card section">
              <span className="eyebrow">{t('dash.invested')}</span>
              <div className="big-number"><span>{formatMoney(figures.invested)}</span></div>
              <span className="muted small">
                {t('dash.investedSplit', { goods: formatMoney(figures.boughtFor), shipping: formatMoney(figures.shipping) })}
              </span>
              {before && <Delta now={figures.invested} before={before.invested} tone="neutral" />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.revenue')}</span>
              <div className="big-number"><span>{formatMoney(figures.revenue)}</span></div>
              <span className="muted small">
                {figures.salesCount === 1 ? t('dash.salesCountOne') : t('dash.salesCount', { n: figures.salesCount })}
              </span>
              {before && <Delta now={figures.revenue} before={before.revenue} tone="up-good" />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.costs')}</span>
              <div className="big-number"><span>{formatMoney(figures.costs)}</span></div>
              {Number(figures.losses.amount) > 0 && (
                <span className="muted small">
                  {t('dash.costsSplit', { expenses: formatMoney(figures.expenses), losses: formatMoney(figures.losses) })}
                </span>
              )}
              <Link className="small" to="/expenses">{t('dash.costsLink')}</Link>
              {before && <Delta now={figures.costs} before={before.costs} tone="up-bad" />}
            </div>
            <div className="card section">
              <span className="eyebrow">{t('dash.profit')}</span>
              <div className="big-number">
                <span className={profitClass}>{formatSigned(figures.profit)}</span>
              </div>
              <span className="muted small">
                {figures.marginPercent !== null ? t('dash.margin', { p: figures.marginPercent }) : t('dash.noSales')}
                {figures.unknownProfitCount > 0 && (
                  <>
                    {' · '}
                    <span className="warning-text">
                      {t('dash.notCounted', { n: figures.unknownProfitCount })} <Link to="/exchange-rates">{t('dash.addRate')}</Link>
                    </span>
                  </>
                )}
              </span>
              {before && <Delta now={figures.profit} before={before.profit} tone="up-good" />}
            </div>
          </div>

          <div className="card section">
            <div className="spread">
              <span>
                <span className="eyebrow">{t('dash.inStock')}</span>{' '}
                <strong>{t('dash.pieces', { n: data.stockPieces })}</strong>
                <span className="muted"> {t('dash.worth', { value: formatMoney(data.position.stockValue) })}</span>
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
              balances={data.position.owedToYou}
              link="/sales"
            />
            <OpenBalances
              title={t('dash.youOwe')}
              empty={t('dash.youOweNone')}
              balances={data.position.youOwe}
              link="/purchases"
            />
          </div>
        </>
      )}
    </Layout>
  )
}

type DeltaProps = { now: api.Money; before: api.Money; tone: 'up-good' | 'up-bad' | 'neutral' }

/** "▲ 12% vs previous period", green when the change is good for the business, red when bad. */
function Delta({ now, before: previous, tone }: DeltaProps) {
  const t = useT()
  const before = Number(previous.amount)
  const change = Number(now.amount) - before
  const percent = before !== 0 ? Math.round((change / Math.abs(before)) * 1000) / 10 : null
  const mood = change === 0 || tone === 'neutral' ? 'neutral' : (change > 0) === (tone === 'up-good') ? 'good' : 'bad'
  const arrow = change > 0 ? '▲' : change < 0 ? '▼' : '='
  const amount = percent !== null ? `${Math.abs(percent)}%` : change === 0 ? '0%' : t('dash.newVsPrev')
  return (
    <span className={`delta ${mood}`} title={t('dash.prevWas', { value: formatMoney(previous) })}>
      <span aria-hidden="true">{arrow}</span>
      <span>
        {change < 0 ? '−' : change > 0 ? '+' : ''}
        {amount}
      </span>
      <span className="muted">{t('dash.vsPrev')}</span>
      <span className="sr-only">{t('dash.prevWas', { value: formatMoney(previous) })}</span>
    </span>
  )
}

type OpenProps = { title: string; empty: string; balances: api.ReportOpenBalances; link: string }

function OpenBalances({ title, empty, balances, link }: OpenProps) {
  const t = useT()
  const rows = balances.rows
  const sorted = [...rows].sort((a, b) => b.daysOpen - a.daysOpen)
  return (
    <section className="card table-card">
      <div className="card-head center">
        <h2 className="h-sm">{title}</h2>
        <span className="muted small">{formatMoney(balances.total)}</span>
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
                  <td className={`num small${r.overdue ? ' warning-text' : ' muted'}`}>{t('dash.days', { n: r.daysOpen })}</td>
                  <td className="num strong">
                    {formatMoney(r.converted ?? r.balance)}
                    {r.converted && r.converted.currency !== r.balance.currency && (
                      <div className="muted small">{formatMoney(r.balance)}</div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
