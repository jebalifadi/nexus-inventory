import { useEffect, useState } from 'react'
import * as api from '../api'
import { useIsOwner } from '../auth'
import { currencyDecimals, currencyStep, EXPENSE_CATEGORY_LABELS, formatDate, formatMoney, today } from '../catalog'
import { ActionForm, Dialog } from '../components/Dialog'
import { Layout } from '../components/Layout'

type Data = {
  expenses: api.Expense[]
  units: api.Unit[]
  sales: api.Sale[]
  products: api.Product[]
  currencies: string[]
}

export function ExpensesPage() {
  const isOwner = useIsOwner()
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.fetchExpenses(), api.fetchUnits(), api.fetchSales(), api.fetchProducts(), api.fetchCurrencies()])
      .then(([expenses, units, sales, products, currencies]) => setData({ expenses, units, sales, products, currencies }))
      .catch((e: Error) => setError(e.message))
  }, [])

  const productName = (unit: api.Unit | undefined) => data?.products.find((p) => p.id === unit?.productId)?.name

  function belongsTo(expense: api.Expense) {
    if (expense.unitId) {
      const name = productName(data?.units.find((u) => u.id === expense.unitId))
      return `Piece ${expense.unitId}${name ? ` · ${name}` : ''}`
    }
    if (expense.saleId) return `Sale ${expense.saleId}`
    return <span className="muted">General</span>
  }

  return (
    <Layout>
      <div className="page-head">
        <h1>Expenses</h1>
        {isOwner && data && (
          <button
            onClick={() => {
              setNotice(null)
              setAdding(true)
            }}
          >
            Add expense
          </button>
        )}
      </div>

      {notice && <p className="success" role="status">{notice}</p>}
      {error && <div className="card error-box" role="alert"><p>{error}</p></div>}
      {!data && !error && <p className="muted">Loading…</p>}

      {data?.expenses.length === 0 && (
        <div className="card empty">
          <strong>No expenses yet</strong>
          <span className="muted">Costs outside the purchase price, such as repairs, fees or packaging, show up here.</span>
        </div>
      )}

      {data && data.expenses.length > 0 && (
        <div className="card table-card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Expense</th>
                <th>Date</th>
                <th>Category</th>
                <th>Description</th>
                <th>Belongs to</th>
                <th className="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.expenses.map((expense) => (
                <tr key={expense.id}>
                  <td className="sku">{expense.id}</td>
                  <td className="nowrap">{formatDate(expense.expenseDate)}</td>
                  <td>{EXPENSE_CATEGORY_LABELS[expense.category]}</td>
                  <td>{expense.description ?? <span className="muted">—</span>}</td>
                  <td>{belongsTo(expense)}</td>
                  <td className="num">{formatMoney(expense.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding && data && (
        <AddExpenseDialog
          data={data}
          productName={productName}
          onSaved={async (expense) => {
            setData({ ...data, expenses: await api.fetchExpenses() })
            setNotice(`Expense ${expense.id} saved.`)
          }}
          onClose={() => setAdding(false)}
        />
      )}
    </Layout>
  )
}

type DialogProps = {
  data: Data
  productName: (unit: api.Unit | undefined) => string | undefined
  onSaved: (expense: api.Expense) => Promise<void>
  onClose: () => void
}

/** Where the expense lands in profit. No default: choosing wrongly would quietly change the numbers. */
type Target = 'PIECE' | 'SALE' | 'GENERAL'

function AddExpenseDialog({ data, productName, onSaved, onClose }: DialogProps) {
  const [currency, setCurrency] = useState(data.currencies.includes('EUR') ? 'EUR' : (data.currencies[0] ?? ''))
  const [target, setTarget] = useState<Target | null>(null)
  const step = currencyStep(currency)

  async function save(form: FormData) {
    const expense = await api.createExpense({
      category: form.get('category') as api.ExpenseCategory,
      amount: { amount: String(form.get('amount')), currency },
      expenseDate: String(form.get('expenseDate')),
      description: String(form.get('description')),
      unitId: target === 'PIECE' ? String(form.get('unitId')) : undefined,
      saleId: target === 'SALE' ? String(form.get('saleId')) : undefined,
    })
    await onSaved(expense)
  }

  return (
    <Dialog title="Add expense" onClose={onClose}>
      <ActionForm submitLabel="Save expense" busyLabel="Saving…" onSubmit={save} onClose={onClose}>
        <label>
          Category
          <select name="category" defaultValue="" required>
            <option value="" disabled>
              Choose…
            </option>
            {Object.entries(EXPENSE_CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
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
        <label>
          Date
          <input name="expenseDate" type="date" defaultValue={today()} max={today()} required />
        </label>
        <label>
          <span>
            Description <span className="muted">(optional)</span>
          </span>
          <input name="description" maxLength={255} />
        </label>

        <div className="subform" role="radiogroup" aria-label="What does this expense belong to?">
          <span className="label">What does it belong to?</span>
          <label className="choice">
            <input type="radio" name="target" checked={target === 'PIECE'} onChange={() => setTarget('PIECE')} required />
            <span>
              One piece <span className="muted">(repair, parts): adds to that piece's cost</span>
            </span>
          </label>
          <label className="choice">
            <input type="radio" name="target" checked={target === 'SALE'} onChange={() => setTarget('SALE')} />
            <span>
              One sale <span className="muted">(fees): lowers that sale's profit</span>
            </span>
          </label>
          <label className="choice">
            <input type="radio" name="target" checked={target === 'GENERAL'} onChange={() => setTarget('GENERAL')} />
            <span>
              The whole business <span className="muted">(not tied to a piece or a sale)</span>
            </span>
          </label>
        </div>

        {target === 'PIECE' && (
          <label>
            Which piece
            <select name="unitId" defaultValue="" required>
              <option value="" disabled>
                {data.units.length ? 'Choose…' : 'No pieces yet'}
              </option>
              {data.units.map((unit) => (
                <option key={unit.id} value={unit.id}>
                  {[unit.id, productName(unit), ...Object.values(unit.attributes)].filter(Boolean).join(' · ')}
                </option>
              ))}
            </select>
          </label>
        )}
        {target === 'SALE' && (
          <label>
            Which sale
            <select name="saleId" defaultValue="" required>
              <option value="" disabled>
                {data.sales.length ? 'Choose…' : 'No sales yet'}
              </option>
              {data.sales.map((sale) => (
                <option key={sale.id} value={sale.id}>
                  {sale.id} · {formatDate(sale.saleDate)} · {sale.customerName ?? 'Walk-in'} · {formatMoney(sale.total)}
                </option>
              ))}
            </select>
          </label>
        )}
      </ActionForm>
    </Dialog>
  )
}
