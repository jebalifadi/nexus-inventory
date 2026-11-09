import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { formatDate, formatMoney, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'
import { defaultCurrency } from '../i18n/context'

/** The database keeps 8 decimals and rounds anything longer, so the form stops there. */
const RATE_STEP = '0.00000001'
/** Further than this from the last rate, the form asks for a second look: rates cannot be changed later. */
const WARN_DIFFERENCE = 0.2

const showRate = (rate: number) => Number(rate.toFixed(4)).toString()

export function ExchangeRatesPage() {
  const isOwner = useIsOwner()
  const [rates, setRates] = useState<api.ExchangeRate[] | null>(null)
  const [currencies, setCurrencies] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.fetchExchangeRates(), api.fetchCurrencies()])
      .then(([loadedRates, loadedCurrencies]) => {
        setRates(loadedRates)
        setCurrencies(loadedCurrencies)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <Layout>
      <div className="page-head">
        <h1>Exchange rates</h1>
        {isOwner && rates && (
          <button
            onClick={() => {
              setNotice(null)
              setAdding(true)
            }}
          >
            Add rate
          </button>
        )}
      </div>

      <p className="muted">
        A rate applies from its date until the next rate for the same currencies. A sale uses the rate valid on its sale
        day, so a past sale needs a rate dated on or before that day.
      </p>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!rates && !error && <p className="muted">Loading…</p>}

      {rates?.length === 0 && (
        <div className="card empty">
          <strong>No exchange rates yet</strong>
          <span className="muted">Without a rate, the profit of a sale that mixes currencies shows as Unknown.</span>
        </div>
      )}

      {rates && rates.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Valid from</th>
                <th>Rate</th>
                <th>The other way</th>
              </tr>
            </thead>
            <tbody>
              {rates.map((rate) => (
                <tr key={rate.id}>
                  <td className="nowrap">
                    {formatDate(rate.rateDate)}
                    {rate.source !== 'MANUAL' && <div className="muted small">{rate.source}</div>}
                  </td>
                  <td className="strong nowrap">
                    1 {rate.base} = {rate.rate} {rate.quote}
                  </td>
                  <td className="muted nowrap">
                    1 {rate.quote} = {showRate(1 / rate.rate)} {rate.base}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && rates && (
        <AddRateDialog
          rates={rates}
          currencies={currencies}
          onSaved={async (saved) => {
            setRates(await api.fetchExchangeRates())
            setNotice(`Saved. Sales from ${formatDate(saved.rateDate)} on now use 1 ${saved.base} = ${saved.rate} ${saved.quote}, until the next rate.`)
          }}
          onClose={() => setAdding(false)}
        />
      )}
    </Layout>
  )
}

type DialogProps = {
  rates: api.ExchangeRate[]
  currencies: string[]
  onSaved: (saved: api.ExchangeRate) => Promise<void>
  onClose: () => void
}

function AddRateDialog({ rates, currencies, onSaved, onClose }: DialogProps) {
  const [base, setBase] = useState(defaultCurrency(currencies))
  const [quote, setQuote] = useState(currencies.includes('TND') ? 'TND' : (currencies[1] ?? ''))
  const [rate, setRate] = useState('')
  const [rateDate, setRateDate] = useState(today)

  // Choosing the currency already on the other side swaps them, so the two can never match.
  function chooseBase(value: string) {
    if (value === quote) setQuote(base)
    setBase(value)
  }
  function chooseQuote(value: string) {
    if (value === base) setBase(quote)
    setQuote(value)
  }

  const value = Number(rate)
  const entered = value > 0

  // The newest rate between these two currencies, written in the direction being entered.
  const last = rates.find((r) => (r.base === base && r.quote === quote) || (r.base === quote && r.quote === base))
  const lastValue = last && (last.base === base ? last.rate : 1 / last.rate)
  const farFromLast = entered && lastValue !== undefined && Math.abs(value - lastValue) / lastValue > WARN_DIFFERENCE

  async function save() {
    // One rate per direction and day; say so plainly instead of a server error. A rate you enter replaces the bank's.
    if (rates.some((r) => r.source === 'MANUAL' && r.base === base && r.quote === quote && r.rateDate === rateDate)) {
      throw new Error(`There is already a ${base} to ${quote} rate for ${formatDate(rateDate)}, and it cannot be changed.`)
    }
    await onSaved(await api.createExchangeRate({ base, quote, rate, rateDate }))
  }

  return (
    <Dialog title="Add exchange rate" onClose={onClose}>
      <ActionForm submitLabel="Save rate" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <div className="row compact">
          <label className="narrow">
            1
            <select aria-label="From currency" value={base} onChange={(e) => chooseBase(e.target.value)}>
              {currencies.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label>
            equals
            <input
              aria-label="Rate"
              type="number"
              inputMode="decimal"
              min={RATE_STEP}
              step={RATE_STEP}
              value={rate}
              onChange={(e) => setRate(e.target.value)}
              placeholder="3.3798"
              required
            />
          </label>
          <label className="narrow">
            of
            <select aria-label="To currency" value={quote} onChange={(e) => chooseQuote(e.target.value)}>
              {currencies.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Valid from
          <input type="date" value={rateDate} max={today()} onChange={(e) => setRateDate(e.target.value)} required />
        </label>

        {entered && (
          <div className="preview" role="status">
            <strong>
              {formatMoney({ amount: '100', currency: base })} = {formatMoney({ amount: String(100 * value), currency: quote })}
            </strong>
            <span className="muted">
              1 {quote} = {showRate(1 / value)} {base}
            </span>
          </div>
        )}
        {last && lastValue !== undefined && (
          <p className="muted small">
            Last rate: 1 {base} = {showRate(lastValue)} {quote} on {formatDate(last.rateDate)}
          </p>
        )}
        {farFromLast && (
          <p className="error" role="alert">
            This is far from the last rate. Please check it, including which currency comes first.
          </p>
        )}
        <p className="muted small">Saved rates cannot be changed or deleted yet, and they can change the profit of past sales.</p>
      </ActionForm>
    </Dialog>
  )
}
