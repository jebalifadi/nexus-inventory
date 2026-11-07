import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { currencyStep, formatDate, formatMoney, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

const STATUS_LABELS: Record<api.ClaimStatus, string> = { OPEN: 'Open', RESOLVED: 'Resolved', REJECTED: 'Rejected' }
const RESOLUTION_LABELS: Record<api.ClaimResolution, string> = {
  REPAIRED: 'Repaired',
  REPLACED: 'Replaced',
  REFUNDED: 'Refunded (taken back)',
  REJECTED: 'Rejected',
}

type Data = { claims: api.WarrantyClaim[]; sales: api.Sale[]; products: api.Product[]; currencies: string[] }

async function load(): Promise<Data> {
  const [claims, sales, products, currencies] = await Promise.all([
    api.fetchWarrantyClaims(),
    api.fetchSales(),
    api.fetchProducts(),
    api.fetchCurrencies(),
  ])
  claims.sort((a, b) => Number(b.status === 'OPEN') - Number(a.status === 'OPEN') || b.claimDate.localeCompare(a.claimDate))
  return { claims, sales, products, currencies }
}

export function WarrantyPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [opening, setOpening] = useState(false)
  const [resolving, setResolving] = useState<api.WarrantyClaim | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    load()
      .then(setData)
      .catch((e: Error) => setError(e.message))
  }, [])

  const productName = (id: number) => data?.products.find((p) => p.id === id)?.name ?? `Product ${id}`
  function claimItem(claim: api.WarrantyClaim) {
    const line = data?.sales.find((s) => s.id === claim.saleId)?.lines.find((l) => l.lineNo === claim.lineNo)
    if (!line) return `Line ${claim.lineNo}`
    return `${claim.quantity} × ${productName(line.productId)}`
  }

  async function saved(text: string) {
    setData(await load())
    setNotice(text)
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Warranty and returns</h1>
        {isOwner && data && (
          <button
            onClick={() => {
              setNotice(null)
              setOpening(true)
            }}
            disabled={data.sales.length === 0}
          >
            New claim
          </button>
        )}
      </div>

      <p className="muted">
        A customer comes back with a problem. Repair or replacement costs lower that sale's profit; a refund takes the
        goods back into stock and gives the money back.
      </p>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data?.claims.length === 0 && (
        <div className="card empty">
          <strong>No claims yet</strong>
          <span className="muted">When a customer brings something back, record it here.</span>
        </div>
      )}

      {data && data.claims.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Claim</th>
                <th>Date</th>
                <th>Sale</th>
                <th>Item</th>
                <th>Issue</th>
                <th>Warranty</th>
                <th>Status</th>
                <th className="num">Cost / refund</th>
                {isOwner && <th><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {data.claims.map((c) => (
                <tr key={c.id}>
                  <td className="sku">{c.id}</td>
                  <td className="nowrap">{formatDate(c.claimDate)}</td>
                  <td className="sku">{c.saleId}</td>
                  <td>
                    {claimItem(c)}
                    {c.unitId && <div className="muted small sku">{c.unitId}</div>}
                  </td>
                  <td style={{ whiteSpace: 'normal', minWidth: 180 }}>{c.issue}</td>
                  <td className="nowrap">
                    {c.withinWarranty ? (
                      <span>Within{c.warrantyEndDate && <span className="muted small"> (to {formatDate(c.warrantyEndDate)})</span>}</span>
                    ) : (
                      <span className="warning-text" title={c.goodwillReason ?? undefined}>Goodwill</span>
                    )}
                  </td>
                  <td className="nowrap">
                    <span className={`status ${c.status === 'OPEN' ? 'status-reserved' : c.status === 'RESOLVED' ? 'status-in_inventory' : ''}`}>
                      {c.resolution && c.status !== 'REJECTED' ? RESOLUTION_LABELS[c.resolution] : STATUS_LABELS[c.status]}
                    </span>
                  </td>
                  <td className="num">
                    {c.cost && <div>{formatMoney(c.cost)}</div>}
                    {c.refund && <div className="muted small">refund {formatMoney(c.refund)}</div>}
                    {!c.cost && !c.refund && <span className="muted">—</span>}
                  </td>
                  {isOwner && (
                    <td className="row-actions">
                      {c.status === 'OPEN' && (
                        <button className="secondary" onClick={() => setResolving(c)}>
                          Close claim
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {opening && data && (
        <NewClaimDialog
          sales={data.sales}
          productName={productName}
          onClose={() => setOpening(false)}
          onSaved={(claim) => saved(`Claim ${claim.id} opened.`)}
        />
      )}
      {resolving && data && (
        <CloseClaimDialog
          claim={resolving}
          currencies={data.currencies}
          defaultCurrency={data.sales.find((s) => s.id === resolving.saleId)?.currency ?? data.currencies[0]}
          onClose={() => setResolving(null)}
          onSaved={(claim) => saved(`Claim ${claim.id} closed.`)}
        />
      )}
    </Layout>
  )
}

type NewProps = {
  sales: api.Sale[]
  productName: (id: number) => string
  onSaved: (c: api.WarrantyClaim) => Promise<void>
  onClose: () => void
}

function NewClaimDialog({ sales, productName, onSaved, onClose }: NewProps) {
  const sorted = [...sales].sort((a, b) => b.saleDate.localeCompare(a.saleDate))
  const [saleId, setSaleId] = useState('')
  const [lineNo, setLineNo] = useState<number | null>(null)
  const [claimDate, setClaimDate] = useState(today)
  const sale = sales.find((s) => s.id === saleId)
  const lines = sale?.lines.filter((l) => l.quantity - l.returnedQuantity > 0) ?? []
  const line = lines.find((l) => l.lineNo === lineNo)
  const outside = line ? !line.warrantyEndDate || line.warrantyEndDate < claimDate : false

  async function save(form: FormData) {
    const claim = await api.openWarrantyClaim({
      saleId,
      lineNo: lineNo!,
      quantity: Number(form.get('quantity') ?? 1),
      claimDate,
      issue: String(form.get('issue')),
      goodwillReason: String(form.get('goodwillReason') ?? ''),
    })
    await onSaved(claim)
  }

  return (
    <Dialog title="New warranty claim" onClose={onClose}>
      <ActionForm submitLabel="Open claim" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <label>
          Sale
          <select
            value={saleId}
            onChange={(e) => {
              setSaleId(e.target.value)
              const chosen = sales.find((s) => s.id === e.target.value)
              setLineNo(chosen?.lines.length === 1 ? chosen.lines[0].lineNo : null)
            }}
            required
          >
            <option value="">Choose…</option>
            {sorted.map((s) => (
              <option key={s.id} value={s.id}>
                {s.id} · {formatDate(s.saleDate)} · {s.customerName ?? 'Walk-in'} · {formatMoney(s.total)}
              </option>
            ))}
          </select>
        </label>
        {sale && (
          <label>
            Item
            <select value={lineNo ?? ''} onChange={(e) => setLineNo(e.target.value ? Number(e.target.value) : null)} required>
              <option value="">Choose…</option>
              {lines.map((l) => (
                <option key={l.lineNo} value={l.lineNo}>
                  {l.quantity - l.returnedQuantity} × {productName(l.productId)}
                  {l.unitId ? ` (${l.unitId})` : ''}
                </option>
              ))}
            </select>
          </label>
        )}
        {line && line.quantity - line.returnedQuantity > 1 && (
          <label>
            How many pieces
            <input name="quantity" type="number" min={1} max={line.quantity - line.returnedQuantity} defaultValue={1} required />
          </label>
        )}
        <label>
          Date
          <input type="date" value={claimDate} min={sale?.saleDate} max={today()} onChange={(e) => setClaimDate(e.target.value)} required />
        </label>
        <label>
          What is wrong
          <input name="issue" required maxLength={1000} placeholder="e.g. screen flickers" />
        </label>
        {line && (
          <p className={outside ? 'warning-text' : 'success'}>
            {line.warrantyEndDate
              ? outside
                ? `Warranty ended on ${formatDate(line.warrantyEndDate)}.`
                : `Within warranty (until ${formatDate(line.warrantyEndDate)}).`
              : 'This item was sold without warranty.'}
          </p>
        )}
        {outside && (
          <label>
            Why accept it anyway (goodwill)
            <input name="goodwillReason" required placeholder="e.g. loyal customer" />
          </label>
        )}
      </ActionForm>
    </Dialog>
  )
}

type CloseProps = {
  claim: api.WarrantyClaim
  currencies: string[]
  defaultCurrency: string
  onSaved: (c: api.WarrantyClaim) => Promise<void>
  onClose: () => void
}

function CloseClaimDialog({ claim, currencies, defaultCurrency, onSaved, onClose }: CloseProps) {
  const [resolution, setResolution] = useState<api.ClaimResolution>('REPAIRED')
  const [currency, setCurrency] = useState(defaultCurrency)

  async function save(form: FormData) {
    const resolvedDate = String(form.get('resolvedDate'))
    const notes = String(form.get('notes'))
    if (resolution === 'REJECTED') {
      await onSaved(await api.rejectWarrantyClaim(claim.id, { resolvedDate, notes }))
      return
    }
    const amount = String(form.get('cost') ?? '').trim()
    await onSaved(
      await api.resolveWarrantyClaim(claim.id, {
        resolution,
        cost: amount && Number(amount) > 0 ? { amount, currency } : null,
        resolvedDate,
        notes,
      }),
    )
  }

  return (
    <Dialog title={`Close claim ${claim.id}`} onClose={onClose}>
      <ActionForm
        submitLabel={resolution === 'REJECTED' ? 'Reject claim' : 'Close claim'}
        busyLabel="Saving…"
        danger={resolution === 'REJECTED'}
        onSubmit={save}
        onClose={onClose}
      >
        <p className="muted">{claim.issue}</p>
        <div className="subform" role="radiogroup" aria-label="Outcome">
          {(Object.keys(RESOLUTION_LABELS) as api.ClaimResolution[]).map((r) => (
            <label key={r} className="choice">
              <input type="radio" name="resolution" checked={resolution === r} onChange={() => setResolution(r)} />
              {RESOLUTION_LABELS[r]}
            </label>
          ))}
        </div>
        {resolution === 'REFUNDED' && (
          <p className="hint">The goods go back into stock and what the customer paid for them is given back.</p>
        )}
        {resolution !== 'REJECTED' && (
          <div className="row compact">
            <label>
              <span>What it cost you <span className="muted">(optional)</span></span>
              <input name="cost" type="number" min={0} step={currencyStep(currency)} inputMode="decimal" placeholder="Repair, parts…" />
            </label>
            <label className="narrow">
              Currency
              <select value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {currencies.map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
          </div>
        )}
        <label>
          Date
          <input name="resolvedDate" type="date" min={claim.claimDate} defaultValue={today()} max={today()} required />
        </label>
        <label>
          <span>Notes <span className="muted">(optional)</span></span>
          <input name="notes" />
        </label>
      </ActionForm>
    </Dialog>
  )
}
