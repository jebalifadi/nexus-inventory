import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import * as api from '../api'
import { currencyDecimals, currencyStep, formatMoney, itemFields, PAYMENT_METHOD_LABELS, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { FieldInput } from '../components/FieldInput'
import { Layout } from '../components/Layout'

type Data = {
  products: api.Product[]
  categories: api.Category[]
  locations: api.Location[]
  suppliers: api.Supplier[]
  currencies: string[]
}

type Line = {
  key: number
  productId: number | null
  quantity: string
  unitPrice: string
  /** For pieces tracked one by one: the item fields of each piece (serial number, battery…). */
  pieces: Record<string, string>[]
}

/** No default: "paid" moves money out of the cash book, so the owner decides each time. */
type PaidChoice = 'FULL' | 'PART' | 'NONE'

/** Per-piece details are typed in on screen, so keep one line to a sensible number of pieces. */
const MAX_PIECES_PER_LINE = 50

let nextKey = 1
const emptyLine = (): Line => ({ key: nextKey++, productId: null, quantity: '1', unitPrice: '', pieces: [{}] })

/** Keeps one details entry per piece when the quantity changes, without losing what was typed. */
function resizePieces(pieces: Record<string, string>[], count: number) {
  const size = Math.max(0, Math.min(count, MAX_PIECES_PER_LINE))
  return Array.from({ length: size }, (_, i) => pieces[i] ?? {})
}

export function NewPurchasePage() {
  const navigate = useNavigate()
  const [data, setData] = useState<Data | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [supplierId, setSupplierId] = useState<number | null>(null)
  const [addingSupplier, setAddingSupplier] = useState(false)
  const [locationId, setLocationId] = useState<number | null>(null)
  const [purchaseDate, setPurchaseDate] = useState(today)
  const [currency, setCurrency] = useState('')
  const [lines, setLines] = useState<Line[]>(() => [emptyLine()])
  const [paid, setPaid] = useState<PaidChoice | null>(null)
  const [partAmount, setPartAmount] = useState('')
  const [method, setMethod] = useState<api.PaymentMethod>('BANK_TRANSFER')
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.fetchProducts(), api.fetchCategories(), api.fetchLocations(), api.fetchSuppliers(), api.fetchCurrencies()])
      .then(([products, categories, locations, suppliers, currencies]) => {
        setData({ products, categories, locations, suppliers, currencies })
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
  const products = [...data.products].sort((a, b) => a.sku.localeCompare(b.sku))

  function changeLine(key: number, change: Partial<Line>) {
    setLines((all) => all.map((l) => (l.key === key ? { ...l, ...change } : l)))
  }

  function changeQuantity(line: Line, quantity: string) {
    changeLine(line.key, { quantity, pieces: resizePieces(line.pieces, Number(quantity) || 0) })
  }

  function changePiece(line: Line, index: number, key: string, value: string) {
    changeLine(line.key, { pieces: line.pieces.map((p, i) => (i === index ? { ...p, [key]: value } : p)) })
  }

  // For display and the part-payment check only: the backend works out the real total when saving.
  const decimals = currencyDecimals(currency)
  const step = currencyStep(currency)
  const lineMinorUnits = (line: Line) => (Number(line.quantity) || 0) * Math.round((Number(line.unitPrice) || 0) * 10 ** decimals)
  const total = (lines.reduce((sum, line) => sum + lineMinorUnits(line), 0) / 10 ** decimals).toFixed(decimals)
  const partTooHigh = paid === 'PART' && Number(partAmount) >= Number(total)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!supplierId || !locationId || !paid || partTooHigh) return
    setBusy(true)
    setError(null)

    let purchase: api.Purchase
    try {
      purchase = await api.createPurchase({
        supplierId,
        purchaseDate,
        currency,
        notes,
        lines: lines.map((line) => {
          const category = categoryOf(line.productId)
          const fields = category ? itemFields(category) : []
          const base = {
            productId: line.productId!,
            locationId,
            quantity: Number(line.quantity),
            unitPrice: line.unitPrice,
          }
          if (!isUnitLine(line) || fields.length === 0) return base
          // Only filled-in values are sent; an empty optional field stays empty.
          const items = line.pieces.map((piece) =>
            Object.fromEntries(fields.filter((f) => piece[f.key]?.trim()).map((f) => [f.key, piece[f.key].trim()])),
          )
          return { ...base, items }
        }),
      })
    } catch (e) {
      // All or nothing on the backend: nothing was saved, so the form stays for another try.
      setError((e as Error).message)
      setBusy(false)
      return
    }

    // The purchase exists from here on, so always leave this form: a second submit would buy again.
    const amount = paid === 'FULL' ? purchase.balance.amount : partAmount
    let warning: string | undefined
    if (paid !== 'NONE' && Number(amount) > 0) {
      try {
        await api.recordPurchasePayment(purchase.id, {
          amount: { amount, currency: purchase.currency },
          paymentDate: purchaseDate,
          method,
        })
      } catch (e) {
        warning = `Purchase ${purchase.id} was saved, but its payment was not: ${(e as Error).message} Use "Pay supplier" below to add it.`
      }
    }
    navigate('/purchases', {
      state: warning ? { warning } : { notice: `Purchase ${purchase.id} saved. Its goods are now in stock.` },
    })
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>New purchase</h1>
        <Link to="/purchases">← Back to purchases</Link>
      </div>

      <form className="stack" onSubmit={handleSubmit}>
        <section className="card section">
          <h2>1 · Purchase</h2>
          <div className="row">
            <label>
              Supplier
              <select value={supplierId ?? ''} onChange={(e) => setSupplierId(e.target.value ? Number(e.target.value) : null)} required>
                <option value="">Choose…</option>
                {data.suppliers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                    {s.country ? ` (${s.country})` : ''}
                  </option>
                ))}
              </select>
            </label>
            <button type="button" className="secondary align-end" onClick={() => setAddingSupplier(true)}>
              + New supplier
            </button>
          </div>
          <div className="row">
            <label>
              Goods go to
              <select value={locationId ?? ''} onChange={(e) => setLocationId(e.target.value ? Number(e.target.value) : null)} required>
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
              <input type="date" value={purchaseDate} max={today()} onChange={(e) => setPurchaseDate(e.target.value)} required />
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
          {data.locations.length === 0 && (
            <p className="hint">
              There is no location yet. Add one from <Link to="/stock/new">Add stock</Link> first.
            </p>
          )}
        </section>

        <section className="card section">
          <h2>2 · What was bought</h2>
          {products.length === 0 ? (
            <p className="muted">
              No products yet. Create them in the <Link to="/catalog">Catalog</Link> first.
            </p>
          ) : (
            <>
              {lines.map((line) => {
                const category = categoryOf(line.productId)
                const fields = category && isUnitLine(line) ? itemFields(category) : []
                return (
                  <div className="subform" key={line.key}>
                    <div className="row">
                      <label>
                        Product
                        <select
                          value={line.productId ?? ''}
                          onChange={(e) =>
                            changeLine(line.key, {
                              productId: e.target.value ? Number(e.target.value) : null,
                              quantity: '1',
                              pieces: [{}],
                            })
                          }
                          required
                        >
                          <option value="">Choose…</option>
                          {products.map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.sku} — {p.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="narrow">
                        Quantity
                        <input
                          type="number"
                          min={1}
                          max={isUnitLine(line) && fields.length > 0 ? MAX_PIECES_PER_LINE : 1000}
                          step={1}
                          value={line.quantity}
                          onChange={(e) => changeQuantity(line, e.target.value)}
                          required
                        />
                      </label>
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
                    {fields.length > 0 &&
                      line.pieces.map((piece, index) => (
                        <fieldset key={index}>
                          <legend>Piece {index + 1}</legend>
                          <div className="row">
                            {fields.map((field) => (
                              <FieldInput
                                key={field.key}
                                field={field}
                                value={piece[field.key] ?? ''}
                                onChange={(value) => changePiece(line, index, field.key, value)}
                              />
                            ))}
                          </div>
                        </fieldset>
                      ))}
                  </div>
                )
              })}
              <div className="spread">
                <button type="button" className="secondary" onClick={() => setLines((all) => [...all, emptyLine()])}>
                  + Add another product
                </button>
                <strong>Total: {formatMoney({ amount: total, currency })}</strong>
              </div>
            </>
          )}
        </section>

        <section className="card section">
          <h2>3 · Payment to the supplier</h2>
          <div className="subform" role="radiogroup" aria-label="Have you paid the supplier?">
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
          <button type="submit" disabled={busy || !supplierId || !locationId || products.length === 0 || partTooHigh}>
            {busy ? 'Saving…' : 'Save purchase'}
          </button>
        </div>
      </form>

      {addingSupplier && (
        <NewSupplierDialog
          onClose={() => setAddingSupplier(false)}
          onCreated={(supplier) => {
            setData((d) => d && { ...d, suppliers: [...d.suppliers, supplier] })
            setSupplierId(supplier.id)
          }}
        />
      )}
    </Layout>
  )
}

/** Also used on the Contacts page. */
export function NewSupplierDialog({ onCreated, onClose }: { onCreated: (s: api.Supplier) => void; onClose: () => void }) {
  return (
    <Dialog title="New supplier" onClose={onClose}>
      <ActionForm
        submitLabel="Add supplier"
        busyLabel="Saving…"
        onClose={onClose}
        onSubmit={async (form) => {
          onCreated(
            await api.createSupplier({
              name: String(form.get('name')),
              country: String(form.get('country')),
              contact: String(form.get('contact')),
              notes: String(form.get('notes')),
            }),
          )
        }}
      >
        <label>
          Name
          <input name="name" required autoFocus maxLength={150} />
        </label>
        <label>
          <span>Country <span className="muted">(optional)</span></span>
          <input name="country" maxLength={100} />
        </label>
        <label>
          <span>Contact <span className="muted">(optional: phone, email, website)</span></span>
          <input name="contact" maxLength={255} />
        </label>
        <label>
          <span>Notes <span className="muted">(optional)</span></span>
          <input name="notes" />
        </label>
      </ActionForm>
    </Dialog>
  )
}
