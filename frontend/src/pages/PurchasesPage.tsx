import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { currencyStep, formatDate, formatMoney, PAYMENT_METHOD_LABELS, PAYMENT_STATUS_LABELS, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

export function PurchasesPage() {
  const isOwner = useIsOwner()
  // Set by the New purchase page, to say what was just saved.
  const notice = (useLocation().state as { notice?: string; warning?: string } | null) ?? {}
  const [purchases, setPurchases] = useState<api.Purchase[] | null>(null)
  const [products, setProducts] = useState<api.Product[]>([])
  const [error, setError] = useState<string | null>(null)
  const [paying, setPaying] = useState<api.Purchase | null>(null)

  useEffect(() => {
    Promise.all([api.fetchPurchases(), api.fetchProducts()])
      .then(([loadedPurchases, loadedProducts]) => {
        setPurchases(loadedPurchases)
        setProducts(loadedProducts)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  function items(purchase: api.Purchase) {
    return purchase.lines
      .map((l) => `${l.quantity} × ${products.find((p) => p.id === l.productId)?.name ?? `Product ${l.productId}`}`)
      .join(', ')
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Purchases</h1>
        {isOwner && (
          <Link className="button" to="/purchases/new">
            New purchase
          </Link>
        )}
      </div>

      {notice.notice && <p className="success" role="status">{notice.notice}</p>}
      {notice.warning && <div className="card error-box" role="alert"><p>{notice.warning}</p></div>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!purchases && !error && <p className="muted">Loading…</p>}

      {purchases?.length === 0 && (
        <div className="card empty">
          <strong>No purchases yet</strong>
          <span className="muted">
            Record what you buy from suppliers here. The goods go into stock and what you still owe is tracked.
          </span>
        </div>
      )}

      {purchases && purchases.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Purchase</th>
                <th>Date</th>
                <th>Supplier</th>
                <th>Items</th>
                <th className="num">Total</th>
                <th>Payment</th>
                <th className="num">Still owed</th>
                {isOwner && <th><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {purchases.map((purchase) => (
                <tr key={purchase.id}>
                  <td className="sku">{purchase.id}</td>
                  <td className="nowrap">{formatDate(purchase.purchaseDate)}</td>
                  <td>{purchase.supplierName}</td>
                  <td>{items(purchase)}</td>
                  <td className="num">{formatMoney(purchase.total)}</td>
                  <td className="nowrap">{PAYMENT_STATUS_LABELS[purchase.paymentStatus]}</td>
                  <td className="num">{formatMoney(purchase.balance)}</td>
                  {isOwner && (
                    <td className="row-actions">
                      {Number(purchase.balance.amount) > 0 && (
                        <button className="secondary" onClick={() => setPaying(purchase)}>
                          Pay supplier
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

      {paying && (
        <PaySupplierDialog
          purchase={paying}
          onSaved={async () => setPurchases(await api.fetchPurchases())}
          onClose={() => setPaying(null)}
        />
      )}
    </Layout>
  )
}

type DialogProps = { purchase: api.Purchase; onSaved: () => Promise<void>; onClose: () => void }

function PaySupplierDialog({ purchase, onSaved, onClose }: DialogProps) {
  async function save(form: FormData) {
    await api.recordPurchasePayment(purchase.id, {
      amount: { amount: String(form.get('amount')), currency: purchase.currency },
      paymentDate: String(form.get('paymentDate')),
      method: form.get('method') as api.PaymentMethod,
    })
    await onSaved()
  }

  return (
    <Dialog title={`Payment for ${purchase.id}`} onClose={onClose}>
      <ActionForm submitLabel="Save payment" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <p className="muted">
          You still owe {purchase.supplierName} {formatMoney(purchase.balance)}.
        </p>
        <label>
          Amount ({purchase.currency})
          <input
            name="amount"
            type="number"
            inputMode="decimal"
            min={currencyStep(purchase.currency)}
            max={purchase.balance.amount}
            step={currencyStep(purchase.currency)}
            defaultValue={purchase.balance.amount}
            required
          />
        </label>
        <label>
          Paid on
          <input name="paymentDate" type="date" defaultValue={today()} max={today()} required />
        </label>
        <label>
          Paid by
          <select name="method" defaultValue="BANK_TRANSFER">
            {Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </ActionForm>
    </Dialog>
  )
}
