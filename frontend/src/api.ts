// Talks to the core-service REST API. In development, Vite forwards /api to localhost:8080.

const TOKEN_KEY = 'nexus.token'

export type Session = { token: string; username: string; roles: string[]; expiresAt: string }

export type StockLevel = {
  productId: number
  productName: string
  locationId: number
  locationName: string
  quantity: number
}

export type FieldType = 'TEXT' | 'NUMBER' | 'CHOICE' | 'BOOLEAN' | 'DATE'
/** PRODUCT: same for every piece (model, storage). ITEM: filled in per physical item (IMEI). */
export type FieldScope = 'PRODUCT' | 'ITEM'
/** UNIT: one record per physical item. LOT: tracked by quantity. */
export type TrackingMode = 'UNIT' | 'LOT'

export type CategoryField = {
  id: number
  key: string
  label: string
  type: FieldType
  scope: FieldScope
  required: boolean
  choices: string[]
  /** Place in the product name (0 = first); null when the field is shown as its own column. */
  namePosition: number | null
}

export type Category = { id: number; name: string; trackingMode: TrackingMode; fields: CategoryField[] }

export type NewField = Omit<CategoryField, 'id' | 'key' | 'namePosition'> & { inName: boolean }

export type Product = {
  id: number
  categoryId: number
  sku: string
  name: string
  attributes: Record<string, string>
}

export type Location = { id: number; name: string; country: string }

/** Money is sent as text so amounts are never rounded: { amount: "12.50", currency: "EUR" }. */
export type Money = { amount: string; currency: string }

export type UnitStatus =
  | 'PURCHASED' | 'IN_TRANSIT' | 'IN_INVENTORY' | 'LISTED' | 'RESERVED'
  | 'SOLD' | 'CLOSED' | 'RETURNED' | 'CANCELLED'

/** One individually tracked piece, with its own item fields (serial number, battery…). */
export type Unit = {
  id: string
  productId: number
  locationId: number
  status: UnitStatus
  unitCost: Money
  receivedDate: string
  attributes: Record<string, string>
}

/** A batch of identical goods received together. */
export type Lot = {
  id: string
  productId: number
  locationId: number
  quantityReceived: number
  quantityRemaining: number
  unitCost: Money
  receivedDate: string
  status: string
}

export type NewStock = {
  productId: number
  locationId: number
  unitCost: Money
  receivedDate: string
}

/** Thrown for any non-2xx answer; `message` is the backend's explanation when it gives one. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// --- Session ---------------------------------------------------------------
// The token lives in sessionStorage: it survives a page reload but is gone when the tab closes.

export function loadSession(): Session | null {
  const raw = sessionStorage.getItem(TOKEN_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw) as Session
    if (new Date(session.expiresAt) <= new Date()) {
      clearSession()
      return null
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

function saveSession(session: Session) {
  sessionStorage.setItem(TOKEN_KEY, JSON.stringify(session))
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
}

/** Reads the user name and roles from the token. Only for display: the backend checks the signature. */
function readToken(token: string): { username: string; roles: string[] } {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  const claims = JSON.parse(atob(payload)) as { sub: string; roles?: string[] }
  return { username: claims.sub, roles: claims.roles ?? [] }
}

// --- Requests --------------------------------------------------------------

/** Called when the backend says the token is no longer valid. Set by the auth provider. */
let onUnauthorized: () => void = () => {}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  const session = loadSession()
  if (session) headers.set('Authorization', `Bearer ${session.token}`)

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running?')
  }

  if (!response.ok) {
    if (response.status === 401 && session) onUnauthorized()
    throw new ApiError(response.status, await errorMessage(response))
  }
  if (response.status === 204) return undefined as T // e.g. DELETE: success without a body
  return (await response.json()) as T
}

/** Errors come back as RFC 9457 problem details: { title, detail, ... }. */
async function errorMessage(response: Response): Promise<string> {
  if (response.status >= 500) return 'The server had a problem. Check the backend logs.'
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    return problem.detail || problem.title || response.statusText
  } catch {
    return response.statusText || `Request failed (${response.status})`
  }
}

// --- Endpoints -------------------------------------------------------------

export async function login(username: string, password: string): Promise<Session> {
  const answer = await request<{ token: string; expiresAt: string }>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  const session = { token: answer.token, expiresAt: answer.expiresAt, ...readToken(answer.token) }
  saveSession(session)
  return session
}

export function fetchStock(): Promise<StockLevel[]> {
  return request<StockLevel[]>('/api/v1/stock')
}

export async function fetchCurrencies(): Promise<string[]> {
  return (await request<{ supportedCurrencies: string[] }>('/api/v1/system/info')).supportedCurrencies
}

export function fetchCategories(): Promise<Category[]> {
  return request<Category[]>('/api/v1/categories')
}

export function createCategory(category: { name: string; trackingMode: TrackingMode; fields: NewField[] }) {
  return request<Category>('/api/v1/categories', { method: 'POST', body: JSON.stringify(category) })
}

export function fetchProducts(): Promise<Product[]> {
  return request<Product[]>('/api/v1/products')
}

export function createProduct(product: { categoryId: number; sku: string; attributes: Record<string, string> }) {
  return request<Product>('/api/v1/products', { method: 'POST', body: JSON.stringify(product) })
}

export function fetchLocations(): Promise<Location[]> {
  return request<Location[]>('/api/v1/locations')
}

export function createLocation(location: { name: string; country: string }) {
  return request<Location>('/api/v1/locations', { method: 'POST', body: JSON.stringify(location) })
}

/** One individually tracked item (UNIT categories), with its own item fields such as an IMEI. */
export function receiveUnit(stock: NewStock & { attributes: Record<string, string> }) {
  return request<unknown>('/api/v1/units', { method: 'POST', body: JSON.stringify(stock) })
}

/** A quantity of identical goods (LOT categories). */
export function receiveLot(stock: NewStock & { quantity: number }) {
  return request<unknown>('/api/v1/lots', { method: 'POST', body: JSON.stringify(stock) })
}

/** Renames a category and sets which fields form product names (keys, in order); names are rebuilt. */
export function updateCategory(id: number, change: { name: string; nameFields: string[] }) {
  return request<Category>(`/api/v1/categories/${id}`, { method: 'PUT', body: JSON.stringify(change) })
}

/** Soft delete: the category goes to the trash and can be restored. Refused while it has products. */
export function deleteCategory(id: number, reason: string) {
  return request<void>(`/api/v1/categories/${id}${reasonQuery(reason)}`, { method: 'DELETE' })
}

export function restoreCategory(id: number) {
  return request<Category>(`/api/v1/categories/${id}/restore`, { method: 'POST' })
}

/** Changes SKU and product fields; the backend rebuilds the name. The category cannot change. */
export function updateProduct(id: number, product: { sku: string; attributes: Record<string, string> }) {
  return request<Product>(`/api/v1/products/${id}`, { method: 'PUT', body: JSON.stringify(product) })
}

/** Soft delete: the product goes to the trash and can be restored. Refused while it has stock. */
export function deleteProduct(id: number, reason: string) {
  return request<void>(`/api/v1/products/${id}${reasonQuery(reason)}`, { method: 'DELETE' })
}

export function restoreProduct(id: number) {
  return request<Product>(`/api/v1/products/${id}/restore`, { method: 'POST' })
}

function reasonQuery(reason: string) {
  return reason.trim() ? `?reason=${encodeURIComponent(reason.trim())}` : ''
}

export function fetchUnits(): Promise<Unit[]> {
  return request<Unit[]>('/api/v1/units')
}

export function fetchLots(): Promise<Lot[]> {
  return request<Lot[]>('/api/v1/lots')
}

export function fetchProduct(id: number): Promise<Product> {
  return request<Product>(`/api/v1/products/${id}`)
}

export function fetchCategory(id: number): Promise<Category> {
  return request<Category>(`/api/v1/categories/${id}`)
}

/** Soft delete of one piece: goes to the trash. Refused once sold or while in a shipment. */
export function deleteUnit(id: string, reason: string) {
  return request<void>(`/api/v1/units/${encodeURIComponent(id)}${reasonQuery(reason)}`, { method: 'DELETE' })
}

export function restoreUnit(id: string) {
  return request<Unit>(`/api/v1/units/${encodeURIComponent(id)}/restore`, { method: 'POST' })
}

/** Soft delete of a batch: goes to the trash. Refused once part of it is sold or while in a shipment. */
export function deleteLot(id: string, reason: string) {
  return request<void>(`/api/v1/lots/${encodeURIComponent(id)}${reasonQuery(reason)}`, { method: 'DELETE' })
}

export function restoreLot(id: string) {
  return request<Lot>(`/api/v1/lots/${encodeURIComponent(id)}/restore`, { method: 'POST' })
}

// --- Sales -----------------------------------------------------------------

export type Customer = {
  id: number
  name: string
  phone: string | null
  email: string | null
  country: string | null
  notes: string | null
}

export type PaymentStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID'
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CARD' | 'PAYPAL' | 'OTHER'

export type SaleLine = {
  lineNo: number
  productId: number
  unitId: string | null
  quantity: number
  returnedQuantity: number
  unitPrice: Money
  lineTotal: Money
  /** Empty when the costs are in another currency and no exchange rate is known yet. */
  cost: Money | null
  profit: Money | null
  warrantyDays: number
  warrantyEndDate: string | null
}

export type Sale = {
  id: string
  customerId: number | null
  customerName: string | null
  locationId: number
  saleDate: string
  currency: string
  notes: string | null
  lines: SaleLine[]
  total: Money
  returns: Money
  saleExpenses: Money | null
  profit: Money | null
  /** Says why the profit is empty, for example a missing exchange rate. */
  profitUnknownReason: string | null
  paid: Money
  balance: Money
  paymentStatus: PaymentStatus
}

/** Pieces tracked one by one give a unitId; goods counted by quantity give a productId and quantity. */
export type NewSaleLine = {
  unitId?: string
  productId?: number
  quantity?: number
  unitPrice: string
  warrantyDays?: number
}

export function fetchCustomers(): Promise<Customer[]> {
  return request<Customer[]>('/api/v1/customers')
}

export function createCustomer(customer: { name: string; phone: string }) {
  return request<Customer>('/api/v1/customers', { method: 'POST', body: JSON.stringify(customer) })
}

export function fetchSales(): Promise<Sale[]> {
  return request<Sale[]>('/api/v1/sales')
}

/** All or nothing: takes the goods out of stock and freezes their cost. If one line is wrong, nothing is saved. */
export function createSale(sale: {
  customerId: number | null
  locationId: number
  saleDate: string
  currency: string
  notes: string
  lines: NewSaleLine[]
}) {
  return request<Sale>('/api/v1/sales', { method: 'POST', body: JSON.stringify(sale) })
}

/** Paying more than what is still owed is refused. */
export function recordSalePayment(
  saleId: string,
  payment: { amount: Money; paymentDate: string; method: PaymentMethod },
) {
  return request<unknown>(`/api/v1/sales/${encodeURIComponent(saleId)}/payments`, {
    method: 'POST',
    body: JSON.stringify(payment),
  })
}

// --- Expenses --------------------------------------------------------------

export type ExpenseCategory =
  | 'SHIPPING' | 'REPAIR' | 'TRANSPORT' | 'CUSTOMS' | 'PAYMENT_FEES'
  | 'PLATFORM_FEES' | 'PACKAGING' | 'PARTS' | 'WARRANTY' | 'OTHER'

/** On a piece it adds to that piece's cost; on a sale it lowers that sale's profit; with neither it is a general cost. */
export type Expense = {
  id: string
  category: ExpenseCategory
  amount: Money
  expenseDate: string
  description: string | null
  unitId: string | null
  saleId: string | null
}

export function fetchExpenses(): Promise<Expense[]> {
  return request<Expense[]>('/api/v1/expenses')
}

export function createExpense(expense: {
  category: ExpenseCategory
  amount: Money
  expenseDate: string
  description: string
  unitId?: string
  saleId?: string
}) {
  return request<Expense>('/api/v1/expenses', { method: 'POST', body: JSON.stringify(expense) })
}

// --- Exchange rates --------------------------------------------------------

/** 1 base = rate quote, valid from rateDate until the next rate for the same currencies. */
export type ExchangeRate = {
  id: number
  base: string
  quote: string
  rate: number
  rateDate: string
  source: string
}

export function fetchExchangeRates(): Promise<ExchangeRate[]> {
  return request<ExchangeRate[]>('/api/v1/exchange-rates')
}

/** The rate goes as text so it is never rounded on the way. */
export function createExchangeRate(rate: { base: string; quote: string; rate: string; rateDate: string }) {
  return request<ExchangeRate>('/api/v1/exchange-rates', { method: 'POST', body: JSON.stringify(rate) })
}

// --- Cash and capital ------------------------------------------------------

export type CapitalType = 'OPENING_BALANCE' | 'CAPITAL_ADDED' | 'CAPITAL_WITHDRAWN' | 'PROFIT_WITHDRAWN'

/** What the owner put in and took out, per currency. Kept apart from profit: putting money in is not earning it. */
export type CapitalSummary = {
  currency: string
  added: Money
  withdrawn: Money
  netContribution: Money
  profitWithdrawn: Money
}

/** The business in one currency. Cash is never typed in: it is calculated from every movement. */
export type CurrencyPosition = {
  currency: string
  cash: Money
  stockValue: Money
  receivables: Money
  payables: Money
  businessValue: Money
}

/** One line of the cash book, oldest first. `amount` is positive for money in, negative for money out. */
export type CashMovement = { date: string; type: string; reference: string; amount: Money }

export function fetchCapital(): Promise<CapitalSummary[]> {
  return request<CapitalSummary[]>('/api/v1/capital')
}

export function fetchOverview(): Promise<CurrencyPosition[]> {
  return request<CurrencyPosition[]>('/api/v1/overview')
}

export function fetchCashBook(): Promise<CashMovement[]> {
  return request<CashMovement[]>('/api/v1/cash-book')
}

/** Taking out more cash than there is, is refused by the backend. */
export function recordCapital(movement: { type: CapitalType; amount: Money; date: string }) {
  return request<{ id: string }>('/api/v1/capital', { method: 'POST', body: JSON.stringify(movement) })
}

export function recordCurrencyExchange(exchange: { from: Money; to: Money; date: string; notes: string }) {
  return request<{ id: string }>('/api/v1/currency-exchanges', { method: 'POST', body: JSON.stringify(exchange) })
}

// --- Purchases -------------------------------------------------------------

export type Supplier = { id: number; name: string; country: string | null; contact: string | null; notes: string | null }

export type PurchaseLine = {
  lineNo: number
  productId: number
  locationId: number
  quantity: number
  unitPrice: Money
  lineTotal: Money
  /** The pieces this line put into stock (goods tracked one by one). */
  unitIds: string[]
  /** The batch this line put into stock (goods counted by quantity). */
  lotId: string | null
}

export type Purchase = {
  id: string
  supplierId: number
  supplierName: string
  purchaseDate: string
  currency: string
  notes: string | null
  lines: PurchaseLine[]
  total: Money
  paid: Money
  balance: Money
  paymentStatus: PaymentStatus
}

/** Pieces tracked one by one give `items` (one entry of item fields per piece); others give a quantity. */
export type NewPurchaseLine = {
  productId: number
  locationId: number
  quantity: number
  unitPrice: string
  items?: Record<string, string>[]
}

export function fetchSuppliers(): Promise<Supplier[]> {
  return request<Supplier[]>('/api/v1/suppliers')
}

export function createSupplier(supplier: { name: string; country: string; contact: string; notes: string }) {
  return request<Supplier>('/api/v1/suppliers', { method: 'POST', body: JSON.stringify(supplier) })
}

export function fetchPurchases(): Promise<Purchase[]> {
  return request<Purchase[]>('/api/v1/purchases')
}

/** All or nothing: saves the purchase and puts its goods into stock. If one line is wrong, nothing is saved. */
export function createPurchase(purchase: {
  supplierId: number
  purchaseDate: string
  currency: string
  notes: string
  lines: NewPurchaseLine[]
}) {
  return request<Purchase>('/api/v1/purchases', { method: 'POST', body: JSON.stringify(purchase) })
}

/** Paying more than what is still owed to the supplier is refused. */
export function recordPurchasePayment(
  purchaseId: string,
  payment: { amount: Money; paymentDate: string; method: PaymentMethod },
) {
  return request<unknown>(`/api/v1/purchases/${encodeURIComponent(purchaseId)}/payments`, {
    method: 'POST',
    body: JSON.stringify(payment),
  })
}

/** A sale someone still owes you for (receivable), or a purchase you still owe a supplier for (payable). */
export type OpenBalance = {
  reference: string
  counterparty: string | null
  date: string
  daysOpen: number
  total: Money
  paid: Money
  balance: Money
}

export function fetchReceivables(): Promise<OpenBalance[]> {
  return request<OpenBalance[]>('/api/v1/receivables')
}

export function fetchPayables(): Promise<OpenBalance[]> {
  return request<OpenBalance[]>('/api/v1/payables')
}

// --- Shipments -------------------------------------------------------------

/** BY_VALUE: dearer pieces carry more of the cost. BY_QUANTITY: the same share for every piece. */
export type AllocationMethod = 'BY_VALUE' | 'BY_QUANTITY'

export type ShipmentItem = {
  id: string
  quantity: number
  purchaseCost: Money
  shippingCost: Money
  /** Empty when purchase and shipping are in different currencies and no exchange rate is known yet. */
  landedUnitCost: Money | null
}

export type Shipment = {
  id: string
  fromLocationId: number
  toLocationId: number
  carrier: string | null
  trackingNumber: string | null
  cost: Money
  allocationMethod: AllocationMethod
  status: 'IN_TRANSIT' | 'ARRIVED'
  shippedDate: string
  arrivedDate: string | null
  items: ShipmentItem[]
}

export function fetchShipments(): Promise<Shipment[]> {
  return request<Shipment[]>('/api/v1/shipments')
}

/** The goods go "in transit" until the shipment is marked as arrived. */
export function createShipment(shipment: {
  fromLocationId: number
  toLocationId: number
  carrier: string
  trackingNumber: string
  cost: Money
  allocationMethod: AllocationMethod
  shippedDate: string
  unitIds: string[]
  lotIds: string[]
}) {
  return request<Shipment>('/api/v1/shipments', { method: 'POST', body: JSON.stringify(shipment) })
}

export function markShipmentArrived(id: string, arrivedDate: string) {
  return request<Shipment>(`/api/v1/shipments/${encodeURIComponent(id)}/arrive`, {
    method: 'POST',
    body: JSON.stringify({ arrivedDate }),
  })
}

// --- Warranty --------------------------------------------------------------

export type ClaimStatus = 'OPEN' | 'RESOLVED' | 'REJECTED'
export type ClaimResolution = 'REPAIRED' | 'REPLACED' | 'REFUNDED' | 'REJECTED'

export type WarrantyClaim = {
  id: string
  saleId: string
  lineNo: number
  unitId: string | null
  quantity: number
  claimDate: string
  issue: string
  withinWarranty: boolean
  warrantyEndDate: string | null
  goodwillReason: string | null
  status: ClaimStatus
  resolution: ClaimResolution | null
  cost: Money | null
  refund: Money | null
  resolvedDate: string | null
  notes: string | null
}

export function fetchWarrantyClaims(): Promise<WarrantyClaim[]> {
  return request<WarrantyClaim[]>('/api/v1/warranty-claims')
}

/** Outside the warranty period, a goodwill reason is needed. */
export function openWarrantyClaim(claim: {
  saleId: string
  lineNo: number
  quantity: number
  claimDate: string
  issue: string
  goodwillReason: string
}) {
  return request<WarrantyClaim>('/api/v1/warranty-claims', { method: 'POST', body: JSON.stringify(claim) })
}

/**
 * `cost` (optional) is what the business spent, e.g. the repair; it lowers the sale's profit.
 * REFUNDED also takes the goods back into stock and gives back what the customer paid for them.
 */
export function resolveWarrantyClaim(
  id: string,
  resolution: { resolution: ClaimResolution; cost: Money | null; resolvedDate: string; notes: string },
) {
  return request<WarrantyClaim>(`/api/v1/warranty-claims/${encodeURIComponent(id)}/resolve`, {
    method: 'POST',
    body: JSON.stringify(resolution),
  })
}

export function rejectWarrantyClaim(id: string, rejection: { resolvedDate: string; notes: string }) {
  return request<WarrantyClaim>(`/api/v1/warranty-claims/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify(rejection),
  })
}

// --- Admin -----------------------------------------------------------------

export type TrashType = 'CATEGORY' | 'PRODUCT' | 'LOCATION' | 'UNIT' | 'LOT'
export type TrashItem = { type: TrashType; id: number; name: string; deletedAt: string; reason: string | null }

export function fetchTrash(): Promise<TrashItem[]> {
  return request<TrashItem[]>('/api/v1/trash')
}

/** Soft delete: the location goes to the trash. Refused while it still holds stock. */
export function deleteLocation(id: number, reason: string) {
  return request<void>(`/api/v1/locations/${id}${reasonQuery(reason)}`, { method: 'DELETE' })
}

export function restoreLocation(id: number) {
  return request<Location>(`/api/v1/locations/${id}/restore`, { method: 'POST' })
}

export type Role = 'OWNER' | 'VIEWER'
export type User = { id: number; username: string; role: Role; active: boolean }

export function fetchUsers(): Promise<User[]> {
  return request<User[]>('/api/v1/users')
}

export function createUser(user: { username: string; password: string; role: Role }) {
  return request<User>('/api/v1/users', { method: 'POST', body: JSON.stringify(user) })
}

export type AuditEntry = { id: number; occurredAt: string; username: string; method: string; path: string; status: number }

export function fetchAudit(limit = 200): Promise<AuditEntry[]> {
  return request<AuditEntry[]>(`/api/v1/audit?limit=${limit}`)
}

// --- Write-offs -------------------------------------------------------------

/** Goods that left stock without being sold (damaged, lost). Their real cost lowers profit, not cash. */
export type WriteOff = {
  id: string
  unitId: string | null
  lotId: string | null
  productId: number
  quantity: number
  reason: string
  date: string
  /** Purchase + shipping + repairs, one entry per currency. */
  cost: Money[]
}

export function fetchWriteOffs(): Promise<WriteOff[]> {
  return request<WriteOff[]>('/api/v1/write-offs')
}

/** The piece goes to the trash and its real cost counts as a loss. Restoring it cancels the loss. */
export function writeOffUnit(unitId: string, reason: string) {
  return request<WriteOff>('/api/v1/write-offs', { method: 'POST', body: JSON.stringify({ unitId, reason }) })
}

/** What is left of the batch goes to the trash and its real cost counts as a loss. */
export function writeOffLot(lotId: string, reason: string) {
  return request<WriteOff>('/api/v1/write-offs', { method: 'POST', body: JSON.stringify({ lotId, reason }) })
}
