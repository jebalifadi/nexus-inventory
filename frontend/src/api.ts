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
}

export type Category = { id: number; name: string; trackingMode: TrackingMode; fields: CategoryField[] }

export type NewField = Omit<CategoryField, 'id' | 'key'>

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

export function renameCategory(id: number, name: string) {
  return request<Category>(`/api/v1/categories/${id}`, { method: 'PUT', body: JSON.stringify({ name }) })
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
