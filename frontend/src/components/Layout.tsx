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
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/inventory" end>
            Inventory
          </NavLink>
          <NavLink to="/catalog">Catalog</NavLink>
          <NavLink to="/purchases">Purchases</NavLink>
          <NavLink to="/shipments">Shipments</NavLink>
          <NavLink to="/sales">Sales</NavLink>
          <NavLink to="/warranty">Warranty</NavLink>
          <NavLink to="/expenses">Expenses</NavLink>
          <NavLink to="/cash-book">Cash book</NavLink>
          <NavLink to="/capital">Capital</NavLink>
          <NavLink to="/exchange-rates">Rates</NavLink>
          <NavLink to="/contacts">Contacts</NavLink>
          {isOwner && <NavLink to="/admin">Admin</NavLink>}
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
