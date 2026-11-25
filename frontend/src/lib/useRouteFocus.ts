import { useEffect, useRef, type RefObject } from 'react'
import { useLocation } from 'react-router'

/**
 * After client-side navigation (a pathname change), moves focus to the page's `<h1>` so keyboard
 * and screen-reader users land at the top of the new content (WCAG 2.4.3, DR-11). Query-string
 * changes such as filters and paging do not move focus, and neither does the first render.
 * The heading is made programmatically focusable (`tabIndex=-1`) if the page did not already do so.
 */
export function useRouteFocus(containerRef: RefObject<HTMLElement | null>): void {
  const { pathname } = useLocation()
  const previousPathname = useRef(pathname)

  useEffect(() => {
    if (previousPathname.current === pathname) return
    previousPathname.current = pathname
    const heading = containerRef.current?.querySelector('h1')
    if (!heading) return
    if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1')
    heading.focus()
  }, [pathname, containerRef])
}
