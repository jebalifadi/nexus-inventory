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
