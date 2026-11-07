import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { currencyStep, formatDate, formatMoney, itemFields, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'
import { defaultCurrency } from '../i18n/context'

type Data = {
  shipments: api.Shipment[]
  locations: api.Location[]
  products: api.Product[]
  categories: api.Category[]
  units: api.Unit[]
  lots: api.Lot[]
  currencies: string[]
}

async function load(): Promise<Data> {
  const [shipments, locations, products, categories, units, lots, currencies] = await Promise.all([
    api.fetchShipments(),
    api.fetchLocations(),
    api.fetchProducts(),
    api.fetchCategories(),
    api.fetchUnits(),
    api.fetchLots(),
    api.fetchCurrencies(),
  ])
  shipments.sort((a, b) => b.shippedDate.localeCompare(a.shippedDate) || b.id.localeCompare(a.id))
  return { shipments, locations, products, categories, units, lots, currencies }
}

const ALLOCATION_LABELS: Record<api.AllocationMethod, string> = {
  BY_VALUE: 'By value',
  BY_QUANTITY: 'Same for each piece',
}

export function ShipmentsPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [arriving, setArriving] = useState<api.Shipment | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const locationName = (id: number) => data?.locations.find((l) => l.id === id)?.name ?? `Location ${id}`

  return (
    <Layout>
      <div className="page-head">
        <h1>Shipments</h1>
        {isOwner && data && (
          <button
            onClick={() => {
              setNotice(null)
              setCreating(true)
            }}
            disabled={data.locations.length < 2}
            title={data.locations.length < 2 ? 'You need at least two locations to ship between them' : undefined}
          >
            New shipment
          </button>
        )}
      </div>

      <p className="muted">
        Goods sent from one location to another. The shipping cost is split across the goods, so each piece's real
        cost includes its share.
      </p>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data?.shipments.length === 0 && (
        <div className="card empty">
          <strong>No shipments yet</strong>
          <span className="muted">
            {data.locations.length < 2
              ? 'Add a second location (for example a warehouse in another country) to ship goods between them.'
              : 'Use "New shipment" when you send goods from one location to another.'}
          </span>
        </div>
      )}

      {data && data.shipments.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Shipment</th>
                <th>Sent</th>
                <th>From → To</th>
                <th>Carrier</th>
                <th className="num">Pieces</th>
                <th className="num">Cost</th>
                <th>Status</th>
                <th><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data.shipments.map((s) => (
                <ShipmentRow
                  key={s.id}
                  shipment={s}
                  from={locationName(s.fromLocationId)}
                  to={locationName(s.toLocationId)}
                  open={open === s.id}
                  onToggle={() => setOpen(open === s.id ? null : s.id)}
                  onArrive={isOwner && s.status === 'IN_TRANSIT' ? () => setArriving(s) : undefined}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {creating && data && (
        <NewShipmentDialog
          data={data}
          onClose={() => setCreating(false)}
          onSaved={async (shipment) => {
            setData(await load())
            setNotice(`Shipment ${shipment.id} saved. Its goods are now in transit.`)
          }}
        />
      )}

      {arriving && (
        <Dialog title={`${arriving.id} arrived`} onClose={() => setArriving(null)}>
          <ActionForm
            submitLabel="Mark as arrived"
            busyLabel="Saving…"
            onClose={() => setArriving(null)}
            onSubmit={async (form) => {
              await api.markShipmentArrived(arriving.id, String(form.get('arrivedDate')))
              setData(await load())
              setNotice(`Shipment ${arriving.id} arrived. Its goods are in stock at ${locationName(arriving.toLocationId)}.`)
            }}
          >
            <p className="muted">
              The goods go into stock at {locationName(arriving.toLocationId)} and can be sold from there.
            </p>
            <label>
              Arrived on
              <input name="arrivedDate" type="date" min={arriving.shippedDate} max={today()} defaultValue={today()} required />
            </label>
          </ActionForm>
        </Dialog>
      )}
    </Layout>
  )
}

type RowProps = {
  shipment: api.Shipment
  from: string
  to: string
  open: boolean
  onToggle: () => void
  onArrive?: () => void
}

function ShipmentRow({ shipment: s, from, to, open, onToggle, onArrive }: RowProps) {
  const pieces = s.items.reduce((sum, i) => sum + i.quantity, 0)
  return (
    <>
      <tr>
        <td className="sku">
          <button className="link" onClick={onToggle} aria-expanded={open}>
            {open ? '▾' : '▸'} {s.id}
          </button>
        </td>
        <td className="nowrap">{formatDate(s.shippedDate)}</td>
        <td>
          {from} → {to}
        </td>
        <td>
          {s.carrier ?? <span className="muted">—</span>}
          {s.trackingNumber && <div className="muted small mono-ish">{s.trackingNumber}</div>}
        </td>
        <td className="num">{pieces}</td>
        <td className="num">{formatMoney(s.cost)}</td>
        <td className="nowrap">
          {s.status === 'ARRIVED' ? (
            <span className="status status-in_inventory">Arrived {s.arrivedDate && formatDate(s.arrivedDate)}</span>
          ) : (
            <span className="status status-reserved">In transit</span>
          )}
        </td>
        <td className="row-actions">{onArrive && <button className="secondary" onClick={onArrive}>Arrived</button>}</td>
      </tr>
      {open && (
        <tr>
          <td colSpan={8}>
            <p className="muted small">Cost split: {ALLOCATION_LABELS[s.allocationMethod]}</p>
            <table className="compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">Pieces</th>
                  <th className="num">Bought for (each)</th>
                  <th className="num">Shipping share</th>
                  <th className="num">Real cost (each)</th>
                </tr>
              </thead>
              <tbody>
                {s.items.map((item) => (
                  <tr key={item.id}>
                    <td className="sku">{item.id}</td>
                    <td className="num">{item.quantity}</td>
                    <td className="num">{formatMoney(item.purchaseCost)}</td>
                    <td className="num">{formatMoney(item.shippingCost)}</td>
                    <td className="num">
                      {item.landedUnitCost ? (
                        formatMoney(item.landedUnitCost)
                      ) : (
                        <span className="muted" title="Purchase and shipping are in different currencies; add an exchange rate">
                          Unknown
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </td>
        </tr>
      )}
    </>
  )
}

type DialogProps = { data: Data; onSaved: (s: api.Shipment) => Promise<void>; onClose: () => void }

function NewShipmentDialog({ data, onSaved, onClose }: DialogProps) {
  const [fromId, setFromId] = useState<number | null>(data.locations.length === 2 ? data.locations[0].id : null)
  const [toId, setToId] = useState<number | null>(data.locations.length === 2 ? data.locations[1].id : null)
  const [currency, setCurrency] = useState(defaultCurrency(data.currencies))
  const [chosen, setChosen] = useState<Set<string>>(new Set())

  const product = (id: number) => data.products.find((p) => p.id === id)
  const units = data.units.filter((u) => u.locationId === fromId && u.status === 'IN_INVENTORY')
  const lots = data.lots.filter((l) => l.locationId === fromId && l.status === 'IN_STOCK' && l.quantityRemaining > 0)

  function unitLabel(unit: api.Unit) {
    const p = product(unit.productId)
    const category = data.categories.find((c) => c.id === p?.categoryId)
    const details = (category ? itemFields(category) : []).map((f) => unit.attributes[f.key]).filter(Boolean)
    return [p?.name ?? `Product ${unit.productId}`, ...details].join(' · ')
  }

  function toggle(id: string) {
    setChosen((all) => {
      const next = new Set(all)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function save(form: FormData) {
    if (chosen.size === 0) throw new Error('Choose at least one piece or batch to ship.')
    const shipment = await api.createShipment({
      fromLocationId: fromId!,
      toLocationId: toId!,
      carrier: String(form.get('carrier')),
      trackingNumber: String(form.get('trackingNumber')),
      cost: { amount: String(form.get('cost')), currency },
      allocationMethod: form.get('allocation') as api.AllocationMethod,
      shippedDate: String(form.get('shippedDate')),
      unitIds: units.filter((u) => chosen.has(u.id)).map((u) => u.id),
      lotIds: lots.filter((l) => chosen.has(l.id)).map((l) => l.id),
    })
    await onSaved(shipment)
  }

  return (
    <Dialog title="New shipment" onClose={onClose}>
      <ActionForm submitLabel="Save shipment" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <div className="row compact">
          <label>
            From
            <select
              value={fromId ?? ''}
              onChange={(e) => {
                setFromId(e.target.value ? Number(e.target.value) : null)
                setChosen(new Set())
              }}
              required
            >
              <option value="">Choose…</option>
              {data.locations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            To
            <select value={toId ?? ''} onChange={(e) => setToId(e.target.value ? Number(e.target.value) : null)} required>
              <option value="">Choose…</option>
              {data.locations
                .filter((l) => l.id !== fromId)
                .map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
            </select>
          </label>
        </div>

        <fieldset>
          <legend>What is shipped</legend>
          {!fromId ? (
            <p className="muted">Choose where it is sent from.</p>
          ) : units.length + lots.length === 0 ? (
            <p className="muted">Nothing in stock at this location.</p>
          ) : (
            <div className="stack tight" style={{ maxHeight: 220, overflowY: 'auto' }}>
              {units.map((u) => (
                <label key={u.id} className="choice inline">
                  <input type="checkbox" className="check" checked={chosen.has(u.id)} onChange={() => toggle(u.id)} />
                  <span>
                    <span className="sku">{u.id}</span> {unitLabel(u)}
                  </span>
                </label>
              ))}
              {lots.map((l) => (
                <label key={l.id} className="choice inline">
                  <input type="checkbox" className="check" checked={chosen.has(l.id)} onChange={() => toggle(l.id)} />
                  <span>
                    <span className="sku">{l.id}</span> {l.quantityRemaining} × {product(l.productId)?.name ?? `Product ${l.productId}`}{' '}
                    <span className="muted">(whole batch)</span>
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>

        <div className="row compact">
          <label>
            Shipping cost
            <input name="cost" type="number" min={0} step={currencyStep(currency)} inputMode="decimal" required placeholder="0 if free" />
          </label>
          <label className="narrow">
            Currency
            <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {data.currencies.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Split the cost
          <select name="allocation" defaultValue="BY_VALUE">
            <option value="BY_VALUE">By value: dearer pieces carry more</option>
            <option value="BY_QUANTITY">The same share for each piece</option>
          </select>
        </label>
        <div className="row compact">
          <label>
            <span>Carrier <span className="muted">(optional)</span></span>
            <input name="carrier" maxLength={100} placeholder="DHL, UPS…" />
          </label>
          <label>
            <span>Tracking no. <span className="muted">(optional)</span></span>
            <input name="trackingNumber" maxLength={100} />
          </label>
        </div>
        <label>
          Sent on
          <input name="shippedDate" type="date" defaultValue={today()} max={today()} required />
        </label>
      </ActionForm>
    </Dialog>
  )
}
