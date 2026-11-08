import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { currencyStep, formatDate, formatMoney, formatSigned, profitClass, PAYMENT_METHOD_LABELS, PAYMENT_PILL, PAYMENT_STATUS_LABELS, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

export function SalesPage() {
  const isOwner = useIsOwner()
  // Set by the New sale page, to say what was just saved.
  const notice = (useLocation().state as { notice?: string; warning?: string } | null) ?? {}
  const [sales, setSales] = useState<api.Sale[] | null>(null)
  const [products, setProducts] = useState<api.Product[]>([])
  const [error, setError] = useState<string | null>(null)
  const [paying, setPaying] = useState<api.Sale | null>(null)

  useEffect(() => {
    Promise.all([api.fetchSales(), api.fetchProducts()])
      .then(([loadedSales, loadedProducts]) => {
        setSales(loadedSales)
        setProducts(loadedProducts)
      })
      .catch((e: Error) => setError(e.message))
  }, [])

  function items(sale: api.Sale) {
    return sale.lines
      .map((l) => `${l.quantity} × ${products.find((p) => p.id === l.productId)?.name ?? `Product ${l.productId}`}`)
      .join(', ')
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Sales</h1>
        {isOwner && (
          <Link className="button" to="/sales/new">
            New sale
          </Link>
        )}
      </div>

      {notice.notice && <p className="success" role="status">{notice.notice}</p>}
      {notice.warning && <div className="card error-box" role="alert"><p>{notice.warning}</p></div>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!sales && !error && <p className="muted">Loading…</p>}

      {sales?.length === 0 && (
        <div className="card empty">
          <strong>No sales yet</strong>
          <span className="muted">Each sale you record shows up here with its profit and what is still owed.</span>
        </div>
      )}

      {sales && sales.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Sale</th>
                <th>Date</th>
                <th>Customer</th>
                <th>Items</th>
                <th className="num">Total</th>
                <th className="num">Profit</th>
                <th>Payment</th>
                <th className="num">Still owed</th>
                {isOwner && <th><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {sales.map((sale) => (
                <tr key={sale.id}>
                  <td className="sku">{sale.id}</td>
                  <td className="nowrap">{formatDate(sale.saleDate)}</td>
                  <td>{sale.customerName ?? <span className="muted">Walk-in</span>}</td>
                  <td>{items(sale)}</td>
                  <td className="num">{formatMoney(sale.total)}</td>
                  <td className="num">
                    {sale.profit ? (
                      <span className={profitClass(sale.profit)}>{formatSigned(sale.profit)}</span>
                    ) : (
                      <span className="muted" title={sale.profitUnknownReason ?? undefined}>
                        Unknown
                      </span>
                    )}
                  </td>
                  <td><span className={PAYMENT_PILL[sale.paymentStatus]}>{PAYMENT_STATUS_LABELS[sale.paymentStatus]}</span></td>
                  <td className="num">{formatMoney(sale.balance)}</td>
                  {isOwner && (
                    <td className="row-actions">
                      {Number(sale.balance.amount) > 0 && (
                        <button className="secondary" onClick={() => setPaying(sale)}>
                          Record payment
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
        <RecordPaymentDialog
          sale={paying}
          onSaved={async () => setSales(await api.fetchSales())}
          onClose={() => setPaying(null)}
        />
      )}
    </Layout>
  )
}

type DialogProps = { sale: api.Sale; onSaved: () => Promise<void>; onClose: () => void }

function RecordPaymentDialog({ sale, onSaved, onClose }: DialogProps) {
  async function save(form: FormData) {
    await api.recordSalePayment(sale.id, {
      amount: { amount: String(form.get('amount')), currency: sale.currency },
      paymentDate: String(form.get('paymentDate')),
      method: form.get('method') as api.PaymentMethod,
    })
    await onSaved()
  }

  return (
    <Dialog title={`Payment for ${sale.id}`} onClose={onClose}>
      <ActionForm submitLabel="Save payment" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <p className="muted">
          {sale.customerName ?? 'Walk-in'} still owes {formatMoney(sale.balance)}.
        </p>
        <label>
          Amount ({sale.currency})
          <input
            name="amount"
            type="number"
            inputMode="decimal"
            min={currencyStep(sale.currency)}
            max={sale.balance.amount}
            step={currencyStep(sale.currency)}
            defaultValue={sale.balance.amount}
            required
          />
        </label>
        <label>
          Paid on
          <input name="paymentDate" type="date" defaultValue={today()} max={today()} required />
        </label>
        <label>
          Paid by
          <select name="method" defaultValue="CASH">
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
