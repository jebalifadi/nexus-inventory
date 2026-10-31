// Small helpers shared by the catalog, inventory and product screens.
import type { Category, CategoryField, Money, UnitStatus } from './api'

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
