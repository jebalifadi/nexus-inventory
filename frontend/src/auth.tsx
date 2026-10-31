import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import * as api from './api'

type Auth = {
  session: api.Session | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<Auth | null>(null)

/** Keeps track of who is logged in, for every screen below it. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<api.Session | null>(api.loadSession)

  const logout = useCallback(() => {
    api.clearSession()
    setSession(null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    setSession(await api.login(username, password))
  }, [])

  useEffect(() => {
    // The backend rejected our token (expired or invalid): log out, which shows the login screen.
    api.setUnauthorizedHandler(logout)
    if (!session) return
    // Also log out by ourselves at the moment the token expires.
    const msLeft = new Date(session.expiresAt).getTime() - Date.now()
    const timer = setTimeout(logout, Math.max(msLeft, 0))
    return () => clearTimeout(timer)
  }, [session, logout])

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside <AuthProvider>')
  return auth
}

/** Only the owner may change data; viewers can only look. */
export function useIsOwner(): boolean {
  return useAuth().session?.roles.includes('OWNER') ?? false
}

/** Shows its content only to a logged-in user; everyone else goes to the login screen. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

/** Like RequireAuth, but for owner-only screens: viewers are sent back to the inventory. */
export function RequireOwner({ children }: { children: ReactNode }) {
  const isOwner = useIsOwner()
  return <RequireAuth>{isOwner ? children : <Navigate to="/inventory" replace />}</RequireAuth>
}
