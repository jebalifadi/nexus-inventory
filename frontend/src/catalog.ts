// Small helpers shared by the catalog, inventory and product screens.
import type { CapitalType, Category, CategoryField, ExpenseCategory, Money, PaymentMethod, PaymentStatus, UnitStatus } from './api'

export const CAPITAL_TYPE_LABELS: Record<CapitalType, string> = {
  OPENING_BALANCE: 'Opening balance',
  CAPITAL_ADDED: 'Money added',
  CAPITAL_WITHDRAWN: 'Money withdrawn',
  PROFIT_WITHDRAWN: 'Profit withdrawn',
}

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

/** Number and date format, set from the app's language (see I18nProvider); never the browser's own. */
let displayLocale = 'en-GB'

export function setDisplayLocale(locale: string) {
  displayLocale = locale
}

export const getDisplayLocale = () => displayLocale

/** A count in the app's language: 1,234 in English, 1.234 in German. */
export const formatNumber = (n: number) => n.toLocaleString(displayLocale)

/** "412.00 EUR" → "€412.00" (or "412,00 €" in German), following the chosen language. */
export function formatMoney(money: Money): string {
  try {
    return new Intl.NumberFormat(displayLocale, { style: 'currency', currency: money.currency }).format(
      Number(money.amount),
    )
  } catch {
    return `${money.amount} ${money.currency}`
  }
}

/** Shows a date in the chosen language: "7 Nov 2026", "07.11.2026"… */
export const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString(displayLocale, { dateStyle: 'medium', timeStyle: 'short' })

export function formatDate(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00`).toLocaleDateString(displayLocale, {
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

/** Profit shown with its sign: "+€135.90" or "−€12.00" (a real minus sign, not a hyphen). */
export function formatSigned(money: Money): string {
  const value = Number(money.amount)
  const text = formatMoney({ ...money, amount: String(Math.abs(value)) })
  return value > 0 ? `+${text}` : value < 0 ? `−${text}` : text
}

/** CSS class for a profit: green when positive, red when negative. */
export const profitClass = (money: Money) => (Number(money.amount) < 0 ? 'loss' : Number(money.amount) > 0 ? 'profit' : '')

export const PAYMENT_PILL: Record<PaymentStatus, string> = {
  PAID: 'pill pill-paid',
  PARTIALLY_PAID: 'pill pill-part',
  PENDING: 'pill pill-unpaid',
}
