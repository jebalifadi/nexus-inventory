import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { FieldInput } from '../components/FieldInput'
import { Layout } from '../components/Layout'
import { NewLocationForm } from '../components/NewLocationForm'
import { NewProductForm } from '../components/NewProductForm'

type Data = {
  products: api.Product[]
  categories: api.Category[]
  locations: api.Location[]
  currencies: string[]
}

const today = () => new Date().toLocaleDateString('sv') // YYYY-MM-DD in the local time zone

export function AddStockPage() {
  const [data, setData] = useState<Data | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  // Step 1: product
  const [productId, setProductId] = useState<number | null>(null)
  const [creatingProduct, setCreatingProduct] = useState(false)

  // Step 2: stock
  const [creatingLocation, setCreatingLocation] = useState(false)
  const [locationId, setLocationId] = useState<number | null>(null)
  const [receivedDate, setReceivedDate] = useState(today)
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [itemValues, setItemValues] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.fetchProducts(), api.fetchCategories(), api.fetchLocations(), api.fetchCurrencies()])
      .then(([products, categories, locations, currencies]) => {
        setData({ products, categories, locations, currencies })
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

  const product = data.products.find((p) => p.id === productId)
  const category = data.categories.find((c) => c.id === product?.categoryId)
  const location = data.locations.find((l) => l.id === locationId)
  const isUnit = category?.trackingMode === 'UNIT'
  const itemFields = category?.fields.filter((f) => f.scope === 'ITEM') ?? []

  function chooseProduct(id: number | null) {
    setProductId(id)
    setItemValues({})
    setDone(null)
    setError(null)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!product || !location) return
    setBusy(true)
    setError(null)
    setDone(null)
    const stock = { productId: product.id, locationId: location.id, unitCost: { amount, currency }, receivedDate }
    try {
      if (isUnit) {
        const attributes = Object.fromEntries(
          itemFields.filter((f) => itemValues[f.key]?.trim()).map((f) => [f.key, itemValues[f.key]]),
        )
        await api.receiveUnit({ ...stock, attributes })
        // Keep product, place, date and cost, so the next piece only needs its own details.
        setItemValues({})
        setDone(`Added 1 × ${product.name} at ${location.name}. Fill in the next piece, or go to the inventory.`)
      } else {
        await api.receiveLot({ ...stock, quantity: Number(quantity) })
        setDone(`Added ${quantity} × ${product.name} at ${location.name}.`)
        setQuantity('1')
      }
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Add stock</h1>
        <Link to="/inventory">← Back to inventory</Link>
      </div>

      <section className="card section">
        <h2>1 · Product</h2>
        {creatingProduct ? (
          <NewProductForm
            categories={data.categories}
            onCategoryCreated={(c) => setData((d) => d && { ...d, categories: [...d.categories, c] })}
            onCreated={(p) => {
              setData((d) => d && { ...d, products: [...d.products, p] })
              chooseProduct(p.id)
              setCreatingProduct(false)
            }}
            onCancel={() => setCreatingProduct(false)}
          />
        ) : (
          <div className="row">
            <label>
              Product
              <select value={productId ?? ''} onChange={(e) => chooseProduct(e.target.value ? Number(e.target.value) : null)}>
                <option value="">{data.products.length ? 'Choose…' : 'No products yet: create one'}</option>
                {[...data.products]
                  .sort((a, b) => a.sku.localeCompare(b.sku))
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.sku} — {p.name}
                    </option>
                  ))}
              </select>
            </label>
            <button type="button" className="secondary align-end" onClick={() => setCreatingProduct(true)}>
              + New product
            </button>
          </div>
        )}
      </section>

      <section className="card section">
        <h2>2 · Stock</h2>
        {!product ? (
          <p className="muted">Choose or create a product first.</p>
        ) : creatingLocation ? (
          <NewLocationForm
            onCreated={(l) => {
              setData((d) => d && { ...d, locations: [...d.locations, l] })
              setLocationId(l.id)
              setCreatingLocation(false)
            }}
            onCancel={() => setCreatingLocation(false)}
          />
        ) : (
          <form className="stack" onSubmit={handleSubmit}>
            <p className="muted">
              {product.sku} — {product.name} · {category?.name},{' '}
              {isUnit ? 'counted one by one: each submit adds one piece' : 'counted by quantity'}
            </p>

            <div className="row">
              <label>
                Location
                <select value={locationId ?? ''} onChange={(e) => setLocationId(e.target.value ? Number(e.target.value) : null)} required>
                  <option value="">{data.locations.length ? 'Choose…' : 'No locations yet: create one'}</option>
                  {data.locations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name} ({l.country})
                    </option>
                  ))}
                </select>
              </label>
              <button type="button" className="secondary align-end" onClick={() => setCreatingLocation(true)}>
                + New location
              </button>
            </div>

            <div className="row">
              <label>
                Received on
                <input type="date" value={receivedDate} max={today()} onChange={(e) => setReceivedDate(e.target.value)} required />
              </label>
              {!isUnit && (
                <label>
                  Quantity
                  <input type="number" min={1} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required />
                </label>
              )}
            </div>

            <div className="row">
              <label>
                Cost per piece
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
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

            {isUnit && itemFields.length > 0 && (
              <fieldset>
                <legend>This piece</legend>
                {itemFields.map((field) => (
                  <FieldInput
                    key={field.key}
                    field={field}
                    value={itemValues[field.key] ?? ''}
                    onChange={(value) => setItemValues((v) => ({ ...v, [field.key]: value }))}
                  />
                ))}
              </fieldset>
            )}

            {error && <p className="error" role="alert">{error}</p>}
            {done && <p className="success" role="status">{done}</p>}

            <div className="actions">
              <button type="submit" disabled={busy || !location}>
                {busy ? 'Saving…' : isUnit ? 'Add this piece' : 'Add to stock'}
              </button>
            </div>
          </form>
        )}
      </section>
    </Layout>
  )
}
