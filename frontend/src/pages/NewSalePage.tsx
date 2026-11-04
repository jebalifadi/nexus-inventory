import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import * as api from '../api'
import { currencyDecimals, currencyStep, formatMoney, HELD_STATUSES, itemFields, PAYMENT_METHOD_LABELS, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

type Data = {
  products: api.Product[]
  categories: api.Category[]
  locations: api.Location[]
  customers: api.Customer[]
  stock: api.StockLevel[]
  units: api.Unit[]
  currencies: string[]
}

type Line = {
  key: number
  productId: number | null
  unitId: string
  quantity: string
  unitPrice: string
  warrantyDays: string
}

/** No default: "paid" moves money into the cash book, so the owner decides each time. */
type PaidChoice = 'FULL' | 'PART' | 'NONE'

let nextKey = 1
const emptyLine = (): Line => ({ key: nextKey++, productId: null, unitId: '', quantity: '1', unitPrice: '', warrantyDays: '' })

export function NewSalePage() {
  const navigate = useNavigate()
  const [data, setData] = useState<Data | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [locationId, setLocationId] = useState<number | null>(null)
  const [saleDate, setSaleDate] = useState(today)
  const [currency, setCurrency] = useState('')
  const [customerId, setCustomerId] = useState<number | null>(null)
  const [addingCustomer, setAddingCustomer] = useState(false)
  const [lines, setLines] = useState<Line[]>(() => [emptyLine()])
  const [paid, setPaid] = useState<PaidChoice | null>(null)
  const [partAmount, setPartAmount] = useState('')
  const [method, setMethod] = useState<api.PaymentMethod>('CASH')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      api.fetchProducts(),
      api.fetchCategories(),
      api.fetchLocations(),
      api.fetchCustomers(),
      api.fetchStock(),
      api.fetchUnits(),
      api.fetchCurrencies(),
    ])
      .then(([products, categories, locations, customers, stock, units, currencies]) => {
        setData({ products, categories, locations, customers, stock, units, currencies })
        setCurrency(currencies.includes('EUR') ? 'EUR' : (currencies[0] ?? ''))
        if (locations.length === 1) setLocationId(locations[0].id)
      })
      .catch((e: Error) => setLoadError(e.message))
  }, [])

  if (loadError) {
    return (
      <Layout>
        <div className="card error-box" role="alert"><p>{loadError}</p></div>
      </Layout>
    )
  }
  if (!data) {
    return (
      <Layout>
        <p className="muted">Loading…</p>
      </Layout>
    )
  }

  const categoryOf = (productId: number | null) => {
    const product = data.products.find((p) => p.id === productId)
    return data.categories.find((c) => c.id === product?.categoryId)
  }
  const isUnitLine = (line: Line) => categoryOf(line.productId)?.trackingMode === 'UNIT'
  const levelAt = (productId: number | null) =>
    data.stock.find((s) => s.productId === productId && s.locationId === locationId)?.quantity ?? 0

  const sellable = data.products
    .filter((p) => levelAt(p.id) > 0)
    .sort((a, b) => a.sku.localeCompare(b.sku))

  /** Pieces of this line's product still free here: a piece chosen on another line is left out. */
  function piecesFor(line: Line) {
    const taken = lines.filter((l) => l.key !== line.key).map((l) => l.unitId)
    return data!.units.filter(
      (u) =>
        u.productId === line.productId &&
        u.locationId === locationId &&
        HELD_STATUSES.includes(u.status) &&
        !taken.includes(u.id),
    )
  }

  function pieceLabel(unit: api.Unit, line: Line) {
    const category = categoryOf(line.productId)
    const details = (category ? itemFields(category) : [])
      .map((f) => unit.attributes[f.key])
      .filter(Boolean)
    return [unit.id, ...details].join(' · ')
  }

  /** How many of a by-quantity product this line can still take, after the other lines. */
  function maxQuantity(line: Line) {
    const others = lines
      .filter((l) => l.key !== line.key && l.productId === line.productId)
      .reduce((sum, l) => sum + (Number(l.quantity) || 0), 0)
    return Math.max(levelAt(line.productId) - others, 0)
  }

  function changeLine(key: number, change: Partial<Line>) {
    setLines((all) => all.map((l) => (l.key === key ? { ...l, ...change } : l)))
  }

  function chooseLocation(id: number | null) {
    setLocationId(id)
    setLines([emptyLine()]) // pieces and quantities depend on the place, so start the list again
  }

  // For display and the part-payment check only: the backend works out the real total when saving.
  // Added up in the currency's smallest unit (cents, millimes), so decimals do not drift.
  const decimals = currencyDecimals(currency)
  const step = currencyStep(currency)
  const lineMinorUnits = (line: Line) =>
    (isUnitLine(line) ? 1 : Number(line.quantity) || 0) * Math.round((Number(line.unitPrice) || 0) * 10 ** decimals)
  const total = (lines.reduce((sum, line) => sum + lineMinorUnits(line), 0) / 10 ** decimals).toFixed(decimals)
  const partTooHigh = paid === 'PART' && Number(partAmount) >= Number(total)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!locationId || !paid || partTooHigh) return
    setBusy(true)
    setError(null)

    let sale: api.Sale
    try {
      sale = await api.createSale({
        customerId,
        locationId,
        saleDate,
        currency,
        notes,
        lines: lines.map((line) => ({
          ...(isUnitLine(line)
            ? { unitId: line.unitId }
            : { productId: line.productId ?? undefined, quantity: Number(line.quantity) }),
          unitPrice: line.unitPrice,
          warrantyDays: line.warrantyDays ? Number(line.warrantyDays) : undefined,
        })),
      })
    } catch (e) {
      // All or nothing on the backend: nothing was saved, so the form stays for another try.
      setError((e as Error).message)
      setBusy(false)
      return
    }

    // The sale exists from here on, so always leave this form: a second submit would sell again.
    const amount = paid === 'FULL' ? sale.balance.amount : partAmount
    let warning: string | undefined
    if (paid !== 'NONE' && Number(amount) > 0) {
      try {
        await api.recordSalePayment(sale.id, {
          amount: { amount, currency: sale.currency },
          paymentDate: saleDate,
          method,
        })
      } catch (e) {
        warning = `Sale ${sale.id} was saved, but its payment was not: ${(e as Error).message} Use "Record payment" below to add it.`
      }
    }
    navigate('/sales', { state: warning ? { warning } : { notice: `Sale ${sale.id} saved.` } })
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>New sale</h1>
        <Link to="/sales">← Back to sales</Link>
      </div>

      <form className="stack" onSubmit={handleSubmit}>
        <section className="card section">
          <h2>1 · Sale</h2>
          <div className="row">
            <label>
              Sold from
              <select value={locationId ?? ''} onChange={(e) => chooseLocation(e.target.value ? Number(e.target.value) : null)} required>
                <option value="">Choose…</option>
                {data.locations.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name} ({l.country})
                  </option>
                ))}
              </select>
            </label>
            <label>
              Date
              <input type="date" value={saleDate} max={today()} onChange={(e) => setSaleDate(e.target.value)} required />
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
          <div className="row">
            <label>
              Customer
              <select value={customerId ?? ''} onChange={(e) => setCustomerId(e.target.value ? Number(e.target.value) : null)}>
                <option value="">Walk-in (no name)</option>
                {data.customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
            <button type="button" className="secondary align-end" onClick={() => setAddingCustomer(true)}>
              + New customer
            </button>
          </div>
        </section>

        <section className="card section">
          <h2>2 · What was sold</h2>
          {!locationId ? (
            <p className="muted">Choose where it was sold from first.</p>
          ) : sellable.length === 0 ? (
            <p className="muted">Nothing in stock at this location.</p>
          ) : (
            <>
              {lines.map((line) => (
                <div className="subform" key={line.key}>
                  <div className="row">
                    <label>
                      Product
                      <select
                        value={line.productId ?? ''}
                        onChange={(e) =>
                          changeLine(line.key, { productId: e.target.value ? Number(e.target.value) : null, unitId: '', quantity: '1' })
                        }
                        required
                      >
                        <option value="">Choose…</option>
                        {sellable.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.sku} — {p.name}
                          </option>
                        ))}
                      </select>
                    </label>
                    {line.productId !== null && isUnitLine(line) && (
                      <label>
                        Which piece
                        <select value={line.unitId} onChange={(e) => changeLine(line.key, { unitId: e.target.value })} required>
                          <option value="">Choose…</option>
                          {piecesFor(line).map((u) => (
                            <option key={u.id} value={u.id}>
                              {pieceLabel(u, line)}
                            </option>
                          ))}
                        </select>
                      </label>
                    )}
                    {line.productId !== null && !isUnitLine(line) && (
                      <label className="narrow">
                        Quantity
                        <input
                          type="number"
                          min={1}
                          max={maxQuantity(line)}
                          step={1}
                          value={line.quantity}
                          onChange={(e) => changeLine(line.key, { quantity: e.target.value })}
                          required
                        />
                      </label>
                    )}
                  </div>
                  <div className="row">
                    <label>
                      Price per piece
                      <input
                        type="number"
                        min={0}
                        step={step}
                        inputMode="decimal"
                        placeholder={(0).toFixed(decimals)}
                        value={line.unitPrice}
                        onChange={(e) => changeLine(line.key, { unitPrice: e.target.value })}
                        required
                      />
                    </label>
                    <label>
                      <span>Warranty in days <span className="muted">(optional)</span></span>
                      <input
                        type="number"
                        min={0}
                        max={3650}
                        step={1}
                        placeholder="None"
                        value={line.warrantyDays}
                        onChange={(e) => changeLine(line.key, { warrantyDays: e.target.value })}
                      />
                    </label>
                    {lines.length > 1 && (
                      <button
                        type="button"
                        className="secondary align-end"
                        onClick={() => setLines((all) => all.filter((l) => l.key !== line.key))}
                      >
                        Remove
                      </button>
                    )}
                  </div>
                </div>
              ))}
              <div className="spread">
                <button type="button" className="secondary" onClick={() => setLines((all) => [...all, emptyLine()])}>
                  + Add another item
                </button>
                <strong>Total: {formatMoney({ amount: total, currency })}</strong>
              </div>
            </>
          )}
        </section>

        <section className="card section">
          <h2>3 · Payment</h2>
          <div className="subform" role="radiogroup" aria-label="Has the customer paid?">
            <label className="choice">
              <input type="radio" name="paid" checked={paid === 'FULL'} onChange={() => setPaid('FULL')} required />
              Paid in full
            </label>
            <label className="choice">
              <input type="radio" name="paid" checked={paid === 'PART'} onChange={() => setPaid('PART')} />
              Paid part of it
            </label>
            <label className="choice">
              <input type="radio" name="paid" checked={paid === 'NONE'} onChange={() => setPaid('NONE')} />
              Not paid yet
            </label>
          </div>
          {(paid === 'FULL' || paid === 'PART') && (
            <div className="row">
              {paid === 'PART' && (
                <label>
                  Amount paid ({currency})
                  <input
                    type="number"
                    min={step}
                    step={step}
                    inputMode="decimal"
                    value={partAmount}
                    onChange={(e) => setPartAmount(e.target.value)}
                    required
                  />
                </label>
              )}
              <label>
                Paid by
                <select value={method} onChange={(e) => setMethod(e.target.value as api.PaymentMethod)}>
                  {Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}
          {partTooHigh && (
            <p className="error" role="alert">
              A part payment must be less than the total. If it was all paid, choose "Paid in full".
            </p>
          )}
          <label>
            <span>Notes <span className="muted">(optional)</span></span>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
        </section>

        {error && <p className="error" role="alert">{error}</p>}

        <div className="actions">
          <button type="submit" disabled={busy || !locationId || sellable.length === 0 || partTooHigh}>
            {busy ? 'Saving…' : 'Save sale'}
          </button>
        </div>
      </form>

      {addingCustomer && (
        <Dialog title="New customer" onClose={() => setAddingCustomer(false)}>
          <ActionForm
            submitLabel="Add customer"
            busyLabel="Saving…"
            onClose={() => setAddingCustomer(false)}
            onSubmit={async (form) => {
              const customer = await api.createCustomer({
                name: String(form.get('name')),
                phone: String(form.get('phone')),
              })
              setData((d) => d && { ...d, customers: [...d.customers, customer] })
              setCustomerId(customer.id)
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
