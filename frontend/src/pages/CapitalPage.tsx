import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { CAPITAL_TYPE_LABELS, currencyDecimals, currencyStep, formatDate, formatMoney, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

type Data = {
  capital: api.CapitalSummary[]
  positions: api.CurrencyPosition[]
  /** Only the owner's own movements from the cash book, newest first. */
  movements: api.CashMovement[]
  currencies: string[]
}

async function load(): Promise<Data> {
  const [capital, positions, cashBook, currencies] = await Promise.all([
    api.fetchCapital(),
    api.fetchOverview(),
    api.fetchCashBook(),
    api.fetchCurrencies(),
  ])
  const movements = cashBook
    .filter((m) => m.type in CAPITAL_TYPE_LABELS)
    .sort((a, b) => b.date.localeCompare(a.date) || b.reference.localeCompare(a.reference))
  return { capital, positions, movements, currencies }
}

export function CapitalPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const summaryOf = (currency: string) => data?.capital.find((c) => c.currency === currency)
  // A currency with no capital can still hold cash (or be below zero), so it gets a card too.
  const shown = data?.positions.filter((p) => summaryOf(p.currency) || Number(p.cash.amount) !== 0) ?? []

  return (
    <Layout>
      <div className="page-head">
        <h1>Capital</h1>
        {isOwner && data && (
          <button
            onClick={() => {
              setNotice(null)
              setAdding(true)
            }}
          >
            Add entry
          </button>
        )}
      </div>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data && shown.length === 0 && data.movements.length === 0 && (
        <div className="card empty">
          <strong>No capital recorded yet</strong>
          <span className="muted">Start with an opening balance: the cash the business already had when you began using Nexus.</span>
        </div>
      )}

      {shown.length > 0 && (
        <div className="summary-grid">
          {shown.map((position) => {
            const summary = summaryOf(position.currency)
            return (
              <section className="card section" key={position.currency}>
                <div className="stack tight">
                  <span className="eyebrow">Cash · {position.currency}</span>
                  <div className="big-number">
                    <span>{formatMoney(position.cash)}</span>
                  </div>
                </div>
                {summary && (
                  <dl className="details">
                    <div>
                      <dt>Put in</dt>
                      <dd>{formatMoney(summary.added)}</dd>
                    </div>
                    <div>
                      <dt>Taken out</dt>
                      <dd>{formatMoney(summary.withdrawn)}</dd>
                    </div>
                    <div>
                      <dt>Net put in</dt>
                      <dd>{formatMoney(summary.netContribution)}</dd>
                    </div>
                    {Number(summary.profitWithdrawn.amount) > 0 && (
                      <div>
                        <dt>Profit taken out</dt>
                        <dd>{formatMoney(summary.profitWithdrawn)}</dd>
                      </div>
                    )}
                  </dl>
                )}
              </section>
            )
          })}
        </div>
      )}

      {data && data.movements.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Entry</th>
                <th>Date</th>
                <th>Type</th>
                <th className="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.movements.map((movement) => (
                <tr key={movement.reference}>
                  <td className="sku">{movement.reference}</td>
                  <td className="nowrap">{formatDate(movement.date)}</td>
                  <td>{CAPITAL_TYPE_LABELS[movement.type as api.CapitalType]}</td>
                  <td className="num nowrap">{formatMoney(movement.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && data && (
        <AddEntryDialog
          data={data}
          onSaved={async (id) => {
            setData(await load())
            setNotice(`Entry ${id} saved.`)
          }}
          onClose={() => setAdding(false)}
        />
      )}
    </Layout>
  )
}

type DialogProps = { data: Data; onSaved: (id: string) => Promise<void>; onClose: () => void }

/** The three entries the form offers. No default: each one moves the cash figure differently. */
type EntryType = 'OPENING_BALANCE' | 'CAPITAL_ADDED' | 'CAPITAL_WITHDRAWN'

function AddEntryDialog({ data, onSaved, onClose }: DialogProps) {
  const [type, setType] = useState<EntryType | null>(null)
  const [currency, setCurrency] = useState(data.currencies.includes('EUR') ? 'EUR' : (data.currencies[0] ?? ''))
  const step = currencyStep(currency)
  const cash = data.positions.find((p) => p.currency === currency)?.cash ?? { amount: '0', currency }

  async function save(form: FormData) {
    if (!type) return
    // The backend would accept a second one, which would count the same money twice with no way to delete it.
    const already = data.movements.some((m) => m.type === 'OPENING_BALANCE' && m.amount.currency === currency)
    if (type === 'OPENING_BALANCE' && already) {
      throw new Error(`${currency} already has an opening balance. To correct it, use Money added or Money withdrawn.`)
    }
    const saved = await api.recordCapital({
      type,
      amount: { amount: String(form.get('amount')), currency },
      date: String(form.get('date')),
    })
    await onSaved(saved.id)
  }

  return (
    <Dialog title="Add capital entry" onClose={onClose}>
      <ActionForm submitLabel="Save entry" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <div className="subform" role="radiogroup" aria-label="What kind of entry is this?">
          <label className="choice">
            <input type="radio" name="type" checked={type === 'OPENING_BALANCE'} onChange={() => setType('OPENING_BALANCE')} required />
            <span>
              Opening balance <span className="muted">(cash the business had when you began using Nexus)</span>
            </span>
          </label>
          <label className="choice">
            <input type="radio" name="type" checked={type === 'CAPITAL_ADDED'} onChange={() => setType('CAPITAL_ADDED')} />
            <span>
              Money added <span className="muted">(you put your own money in)</span>
            </span>
          </label>
          <label className="choice">
            <input type="radio" name="type" checked={type === 'CAPITAL_WITHDRAWN'} onChange={() => setType('CAPITAL_WITHDRAWN')} />
            <span>
              Money withdrawn <span className="muted">(you take money out)</span>
            </span>
          </label>
        </div>
        <div className="row">
          <label>
            Amount
            <input
              name="amount"
              type="number"
              inputMode="decimal"
              min={step}
              step={step}
              placeholder={(0).toFixed(currencyDecimals(currency))}
              required
            />
          </label>
          <label className="narrow">
            Currency
            <select value={currency} onChange={(e) => setCurrency(e.target.value)} required>
              {data.currencies.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
        </div>
        {type === 'CAPITAL_WITHDRAWN' && <p className="muted small">Cash now: {formatMoney(cash)}</p>}
        <label>
          Date
          <input name="date" type="date" defaultValue={today()} max={today()} required />
        </label>
        <p className="muted small">Saved entries cannot be changed or deleted yet.</p>
      </ActionForm>
    </Dialog>
  )
}
