import type { ReactNode } from 'react'
import { NavLink } from 'react-router'
import { useAuth, useIsOwner } from '../auth'

/** The top bar with navigation and the logged-in user, around every screen after login. */
export function Layout({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth()
  const isOwner = useIsOwner()

  return (
    <>
      <header className="topbar">
        <span className="brand">Nexus</span>
        <nav>
          <NavLink to="/inventory" end>
            Inventory
          </NavLink>
          <NavLink to="/catalog">Catalog</NavLink>
          {isOwner && <NavLink to="/stock/new">Add stock</NavLink>}
        </nav>
        <span className="muted user">
          {session?.username} · {session?.roles.join(', ').toLowerCase()}
        </span>
        <button className="secondary" onClick={logout}>
          Log out
        </button>
      </header>
      <main className="page">{children}</main>
    </>
  )
}
