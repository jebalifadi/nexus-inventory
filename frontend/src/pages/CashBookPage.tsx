import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { CAPITAL_TYPE_LABELS, currencyDecimals, currencyStep, formatDate, formatMoney, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

const TYPE_LABELS: Record<string, string> = {
  ...CAPITAL_TYPE_LABELS,
  SALE_PAYMENT: 'Customer paid',
  SUPPLIER_PAYMENT: 'Paid supplier',
  REFUND: 'Refund to customer',
  EXPENSE: 'Expense',
  EXCHANGE_OUT: 'Currency exchanged (out)',
  EXCHANGE_IN: 'Currency exchanged (in)',
}

/** A cash book line with the balance of its currency right after it. */
type Row = api.CashMovement & { balance: api.Money }

function withBalances(movements: api.CashMovement[]): Row[] {
  const running = new Map<string, number>()
  // The backend sends oldest first; the balance is counted in that order, then shown newest first.
  const rows = movements.map((m) => {
    const decimals = currencyDecimals(m.amount.currency)
    const minor = (running.get(m.amount.currency) ?? 0) + Math.round(Number(m.amount.amount) * 10 ** decimals)
    running.set(m.amount.currency, minor)
    return { ...m, balance: { currency: m.amount.currency, amount: (minor / 10 ** decimals).toFixed(decimals) } }
  })
  return rows.reverse()
}

export function CashBookPage() {
  const isOwner = useIsOwner()
  const [rows, setRows] = useState<Row[] | null>(null)
  const [currencies, setCurrencies] = useState<string[]>([])
  const [filter, setFilter] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [exchanging, setExchanging] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  async function reload() {
    setRows(withBalances(await api.fetchCashBook()))
  }

  useEffect(() => {
    Promise.all([api.fetchCashBook(), api.fetchCurrencies()])
      .then(([movements, loaded]) => {
        setRows(withBalances(movements))
        setCurrencies(loaded)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  const used = [...new Set(rows?.map((r) => r.amount.currency) ?? [])]
  const shown = rows?.filter((r) => !filter || r.amount.currency === filter) ?? []

  return (
    <Layout>
      <div className="page-head">
        <h1>Cash book</h1>
        <div className="head-tools">
          {used.length > 1 && (
            <div className="segmented" role="group" aria-label="Currency">
              <button aria-pressed={filter === ''} onClick={() => setFilter('')}>All</button>
              {used.map((c) => (
                <button key={c} aria-pressed={filter === c} onClick={() => setFilter(c)}>
                  {c}
                </button>
              ))}
            </div>
          )}
          {isOwner && (
            <button
              className="secondary"
              onClick={() => {
                setNotice(null)
                setExchanging(true)
              }}
              disabled={currencies.length < 2}
            >
              Exchange currency
            </button>
          )}
        </div>
      </div>

      <p className="muted">
        Every movement of money, newest first. Cash is never typed in: it comes from sales, payments, expenses and
        capital.
      </p>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!rows && !error && <p className="muted">Loading…</p>}

      {rows?.length === 0 && (
        <div className="card empty">
          <strong>No money movements yet</strong>
          <span className="muted">Start with your opening balance on the Capital page.</span>
        </div>
      )}

      {rows && rows.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>What</th>
                <th>Reference</th>
                <th className="num">In / out</th>
                <th className="num">Balance after</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((r, i) => (
                <tr key={`${r.reference}-${r.type}-${i}`}>
                  <td className="nowrap">{formatDate(r.date)}</td>
                  <td>{TYPE_LABELS[r.type] ?? r.type}</td>
                  <td className="sku">{r.reference}</td>
                  <td className={`num ${Number(r.amount.amount) < 0 ? 'error' : 'success'}`}>
                    {Number(r.amount.amount) > 0 ? '+' : ''}
                    {formatMoney(r.amount)}
                  </td>
                  <td className="num">{formatMoney(r.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {exchanging && (
        <ExchangeDialog
          currencies={currencies}
          onClose={() => setExchanging(false)}
          onSaved={async (text) => {
            await reload()
            setNotice(text)
          }}
        />
      )}
    </Layout>
  )
}

type ExchangeProps = { currencies: string[]; onSaved: (notice: string) => Promise<void>; onClose: () => void }

/** Money changed from one currency to another, e.g. EUR at a bank into TND. */
function ExchangeDialog({ currencies, onSaved, onClose }: ExchangeProps) {
  const [from, setFrom] = useState(currencies[0])
  const [to, setTo] = useState(currencies[1])

  async function save(form: FormData) {
    const given = { amount: String(form.get('fromAmount')), currency: from }
    const got = { amount: String(form.get('toAmount')), currency: to }
    await api.recordCurrencyExchange({ from: given, to: got, date: String(form.get('date')), notes: String(form.get('notes')) })
    await onSaved(`Exchanged ${formatMoney(given)} for ${formatMoney(got)}.`)
  }

  return (
    <Dialog title="Exchange currency" onClose={onClose}>
      <ActionForm submitLabel="Save exchange" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <p className="muted">Money you changed, for example at a bank. Both amounts are what really moved.</p>
        <div className="row compact">
          <label>
            You gave
            <input name="fromAmount" type="number" min={currencyStep(from)} step={currencyStep(from)} inputMode="decimal" required />
          </label>
          <label className="narrow">
            Currency
            <select
              value={from}
              onChange={(e) => {
                const next = e.target.value
                setFrom(next)
                if (next === to) setTo(currencies.find((c) => c !== next) ?? to)
              }}
            >
              {currencies.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
        </div>
        <div className="row compact">
          <label>
            You got
            <input name="toAmount" type="number" min={currencyStep(to)} step={currencyStep(to)} inputMode="decimal" required />
          </label>
          <label className="narrow">
            Currency
            <select value={to} onChange={(e) => setTo(e.target.value)}>
              {currencies
                .filter((c) => c !== from)
                .map((c) => (
                  <option key={c}>{c}</option>
                ))}
            </select>
          </label>
        </div>
        <label>
          Date
          <input name="date" type="date" defaultValue={today()} max={today()} required />
        </label>
        <label>
          <span>Notes <span className="muted">(optional)</span></span>
          <input name="notes" />
        </label>
      </ActionForm>
    </Dialog>
  )
}
