import { useEffect, useRef, useState, useSyncExternalStore, type FormEvent, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useAuth, useIsOwner } from '../auth'
import { Logo } from './Logo'
import { useT } from '../i18n/context'
import type { MessageKey } from '../i18n/en'

type Group = { title: MessageKey | null; links: { to: string; label: MessageKey }[] }

const GROUPS: Group[] = [
  { title: null, links: [{ to: '/dashboard', label: 'nav.dashboard' }, { to: '/analytics', label: 'nav.analytics' }] },
  {
    title: 'nav.stock',
    links: [
      { to: '/inventory', label: 'nav.inventory' },
      { to: '/catalog', label: 'nav.catalog' },
    ],
  },
  {
    title: 'nav.buying',
    links: [
      { to: '/purchases', label: 'nav.purchases' },
      { to: '/shipments', label: 'nav.shipments' },
      { to: '/suppliers', label: 'nav.suppliers' },
    ],
  },
  {
    title: 'nav.selling',
    links: [
      { to: '/sales', label: 'nav.sales' },
      { to: '/warranty', label: 'nav.warranty' },
      { to: '/customers', label: 'nav.customers' },
    ],
  },
  {
    title: 'nav.money',
    links: [
      { to: '/expenses', label: 'nav.expenses' },
      { to: '/cash-book', label: 'nav.cashBook' },
      { to: '/capital', label: 'nav.capital' },
      { to: '/exchange-rates', label: 'nav.exchangeRates' },
    ],
  },
]

/** The pointer must rest this long on a title before it opens, so passing over it does not. */
const OPEN_DELAY_MS = 120
/** Groups close this long after the pointer leaves the menu, so a shaky hand does not close them. */
const CLOSE_DELAY_MS = 350
/** A real mouse and a wide screen: groups open on hover. Phones and tablets: tap to open. */
const HOVER_QUERY = '(hover: hover) and (pointer: fine) and (min-width: 901px)'

function useHoverMenu(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const query = window.matchMedia(HOVER_QUERY)
      query.addEventListener('change', onChange)
      return () => query.removeEventListener('change', onChange)
    },
    () => window.matchMedia(HOVER_QUERY).matches,
  )
}

const navClass = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`

/** The left sidebar and the top bar (search, quick actions), around every screen after login. */
export function Layout({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth()
  const isOwner = useIsOwner()
  const t = useT()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const hoverMenu = useHoverMenu()
  const [navOpen, setNavOpen] = useState(false)
  const [query, setQuery] = useState(() => (location.pathname === '/search' ? (params.get('q') ?? '') : ''))
  const menuButton = useRef<HTMLButtonElement>(null)
  // Groups opened by pointing at them. They stay open while the pointer is in the menu, so the
  // menu never jumps under the mouse; they all close when it leaves. Tapped groups stay open.
  const [hovered, setHovered] = useState<string[]>([])
  const [expanded, setExpanded] = useState<string[]>([])
  const openTimer = useRef<number | undefined>(undefined)
  const closeTimer = useRef<number | undefined>(undefined)

  // Every page renders its own Layout, so moving to another page closes menus by itself.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      if (navOpen) {
        setNavOpen(false)
        menuButton.current?.focus()
      }
      setHovered([])
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      window.clearTimeout(openTimer.current)
      window.clearTimeout(closeTimer.current)
    }
  }, [navOpen])

  const addHovered = (id: string) => setHovered((all) => (all.includes(id) ? all : [...all, id]))
  function hoverGroup(id: string) {
    window.clearTimeout(closeTimer.current)
    window.clearTimeout(openTimer.current)
    openTimer.current = window.setTimeout(() => addHovered(id), OPEN_DELAY_MS)
  }
  function leaveMenu() {
    window.clearTimeout(openTimer.current)
    window.clearTimeout(closeTimer.current)
    closeTimer.current = window.setTimeout(() => setHovered([]), CLOSE_DELAY_MS)
  }

  function search(event: FormEvent) {
    event.preventDefault()
    const q = query.trim()
    if (q) navigate(`/search?q=${encodeURIComponent(q)}`)
  }

  const isCurrent = (to: string) => location.pathname === to || location.pathname.startsWith(`${to}/`)
  const username = session?.username ?? ''
  const role = t(isOwner ? 'role.OWNER' : 'role.VIEWER')

  return (
    <div className={`shell${navOpen ? ' nav-open' : ''}`}>
      <aside className="sidebar" id="sidebar" aria-label={t('nav.mainMenu')}>
        <Link to="/dashboard" className="brand">
          <Logo />
          <span>Nexus</span>
        </Link>
        <nav
          onMouseLeave={hoverMenu ? leaveMenu : undefined}
          onBlur={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setHovered([])
          }}
        >
          {GROUPS.map((group) => {
            const links = group.links.map((link) => (
              <NavLink key={link.to} to={link.to} className={navClass}>
                {t(link.label)}
              </NavLink>
            ))
            if (!group.title) {
              return (
                <div className="nav-group" key="home">
                  {links}
                </div>
              )
            }
            const id = group.title.slice(4) // "nav.buying" → "buying"
            const title = t(group.title)
            const holdsCurrent = group.links.some((link) => isCurrent(link.to))

            const open = holdsCurrent || expanded.includes(id) || (hoverMenu && hovered.includes(id))
            return (
              <div
                className="nav-group"
                key={id}
                onMouseEnter={hoverMenu ? () => hoverGroup(id) : undefined}
                onFocus={(e) => {
                  // Keyboard only: a mouse click on the title is handled by onClick.
                  if (!e.target.matches(':focus-visible')) return
                  window.clearTimeout(closeTimer.current)
                  addHovered(id)
                }}
              >
                <button
                  type="button"
                  className={`nav-title${holdsCurrent ? ' current' : ''}`}
                  aria-expanded={open}
                  aria-controls={`nav-${id}`}
                  onClick={() => {
                    if (holdsCurrent) return // the group you are in always stays open
                    if (open) {
                      setExpanded((all) => all.filter((g) => g !== id))
                      setHovered((all) => all.filter((g) => g !== id))
                    } else {
                      setExpanded((all) => [...all, id])
                    }
                  }}
                >
                  {title}
                  <span className="chevron" aria-hidden="true">›</span>
                </button>
                {/* Always rendered so the height can animate; inert keeps closed links out of Tab. */}
                <div id={`nav-${id}`} className={`nav-collapse${open ? ' open' : ''}`} inert={!open}>
                  <div className="nav-links">{links}</div>
                </div>
              </div>
            )
          })}
        </nav>
        <div className="sidebar-foot">
          <NavLink to="/settings" className={navClass}>
            {t(isOwner ? 'nav.settingsUsers' : 'nav.settings')}
          </NavLink>
          <details className="user-menu">
            <summary aria-label={t('nav.account', { name: username, role })}>
              <span className="avatar" aria-hidden="true">
                {username.slice(0, 1).toUpperCase()}
              </span>
              <span className="who">
                <strong>{username}</strong>
                <span className="muted small">{role}</span>
              </span>
            </summary>
            <div className="menu">
              <button type="button" onClick={logout}>
                {t('nav.logOut')}
              </button>
            </div>
          </details>
        </div>
      </aside>
      {navOpen && <button className="scrim" aria-label={t('nav.closeMenu')} onClick={() => setNavOpen(false)} />}

      <div className="main-col">
        <header className="topbar">
          <button
            ref={menuButton}
            type="button"
            className="menu-button"
            aria-controls="sidebar"
            aria-expanded={navOpen}
            onClick={() => setNavOpen((open) => !open)}
          >
            ☰ <span>{t('nav.menu')}</span>
          </button>
          <form className="search" role="search" onSubmit={search}>
            <label className="sr-only" htmlFor="global-search">
              {t('top.search')}
            </label>
            <input
              id="global-search"
              type="search"
              placeholder={t('top.searchPlaceholder')}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </form>
          {isOwner && (
            <div className="top-actions">
              <Link className="button outline" to="/purchases/new">
                {t('top.recordPurchase')}
              </Link>
              <Link className="button" to="/sales/new">
                {t('top.newSale')}
              </Link>
            </div>
          )}
        </header>
        <main className="page">{children}</main>
      </div>
    </div>
  )
}

