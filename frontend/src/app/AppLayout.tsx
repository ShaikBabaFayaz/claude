import { useRef, type MouseEvent } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { NotificationRegion } from '../components/NotificationProvider'
import { useRouteFocus } from '../lib/useRouteFocus'
import styles from './AppLayout.module.css'
import { RouteErrorBoundary } from './RouteErrorBoundary'

/** Persistent navigation (AC-X.2, FR-23). `end` keeps "Issue & Return" from matching `/loans/overdue`. */
const NAV_ITEMS = [
  { to: '/books', label: 'Books', end: false },
  { to: '/members', label: 'Members', end: false },
  { to: '/loans', label: 'Issue & Return', end: true },
  { to: '/loans/overdue', label: 'Overdue', end: false },
] as const

/**
 * Application shell: skip link, header with persistent `<nav>`, and `<main>` holding the notice
 * region and the current page (architecture 9.1, 9.4). Moves focus to the page heading after
 * navigation (DR-11).
 */
export function AppLayout() {
  const mainRef = useRef<HTMLElement>(null)
  const { pathname } = useLocation()
  useRouteFocus(mainRef)

  function skipToContent(event: MouseEvent<HTMLAnchorElement>) {
    // Focus <main> directly; a hash link would add a fragment to the URL and re-trigger routing.
    event.preventDefault()
    mainRef.current?.focus()
  }

  return (
    <div className={styles.app}>
      <a href="#main-content" className={styles.skipLink} onClick={skipToContent}>
        Skip to main content
      </a>
      <header className={styles.header}>
        <p className={styles.brand}>Library</p>
        <nav aria-label="Main">
          <ul className={styles.navList}>
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className={styles.navLink}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>
      <main id="main-content" ref={mainRef} tabIndex={-1} className={styles.main}>
        <NotificationRegion />
        <RouteErrorBoundary resetKey={pathname}>
          <Outlet />
        </RouteErrorBoundary>
      </main>
    </div>
  )
}
