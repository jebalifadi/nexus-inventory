// Small helpers shared by the catalog, inventory and product screens.
import type { Category, CategoryField, ExpenseCategory, Money, PaymentMethod, PaymentStatus, UnitStatus } from './api'

export const EXPENSE_CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  SHIPPING: 'Shipping',
  REPAIR: 'Repair',
  TRANSPORT: 'Transport',
  CUSTOMS: 'Customs',
  PAYMENT_FEES: 'Payment fees',
  PLATFORM_FEES: 'Platform fees',
  PACKAGING: 'Packaging',
  PARTS: 'Parts',
  WARRANTY: 'Warranty',
  OTHER: 'Other',
}

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  BANK_TRANSFER: 'Bank transfer',
  CARD: 'Card',
  PAYPAL: 'PayPal',
  OTHER: 'Other',
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Not paid',
  PARTIALLY_PAID: 'Part paid',
  PAID: 'Paid',
}

/** How many decimals a currency has: EUR 2, TND 3, JPY 0. The backend rounds amounts to this. */
export function currencyDecimals(currency: string): number {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

/** The smallest amount of a currency, for number inputs: "0.01" for EUR, "0.001" for TND, "1" for JPY. */
export function currencyStep(currency: string): string {
  const decimals = currencyDecimals(currency)
  return decimals === 0 ? '1' : `0.${'0'.repeat(decimals - 1)}1`
}

/** Today as YYYY-MM-DD in the local time zone. */
export const today = () => new Date().toLocaleDateString('sv')

/** The fields that form a product's name, in name order (e.g. Model, Storage). */
export function nameFields(category: Category): CategoryField[] {
  return category.fields
    .filter((f) => f.namePosition !== null)
    .sort((a, b) => (a.namePosition ?? 0) - (b.namePosition ?? 0))
}

/** Product fields that are not in the name: each gets its own labeled column (e.g. Colour). */
export function columnFields(category: Category): CategoryField[] {
  return category.fields.filter((f) => f.scope === 'PRODUCT' && f.namePosition === null)
}

/** Fields filled in for each piece (e.g. Serial number, Battery, Condition). */
export function itemFields(category: Category): CategoryField[] {
  return category.fields.filter((f) => f.scope === 'ITEM')
}

/** Same separator as the backend uses when it builds names. */
export const NAME_SEPARATOR = ' · '

/** Shows a stored value the way people read it: "true" becomes "Yes", empty becomes a dash. */
export function displayValue(field: CategoryField, value: string | undefined): string {
  if (!value) return '—'
  if (field.type === 'BOOLEAN') return value === 'true' ? 'Yes' : 'No'
  return value
}

/** "412.00 EUR" → "€412.00", using the browser's language for the number format. */
export function formatMoney(money: Money): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: money.currency }).format(
      Number(money.amount),
    )
  } catch {
    return `${money.amount} ${money.currency}`
  }
}

export function formatDate(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/** Pieces with these statuses are still held, so they count as stock (same rule as the backend). */
export const HELD_STATUSES: UnitStatus[] = ['IN_INVENTORY', 'LISTED', 'RESERVED']

export const STATUS_LABELS: Record<UnitStatus, string> = {
  PURCHASED: 'Purchased',
  IN_TRANSIT: 'In transit',
  IN_INVENTORY: 'In stock',
  LISTED: 'Listed',
  RESERVED: 'Reserved',
  SOLD: 'Sold',
  CLOSED: 'Closed',
  RETURNED: 'Returned',
  CANCELLED: 'Cancelled',
}
