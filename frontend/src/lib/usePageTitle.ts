import { useEffect } from 'react'

export const APP_TITLE_SUFFIX = 'Library'

/** "Books" -> "Books - Library" (WCAG 2.4.2, DR-11, architecture 9.2). */
export function formatPageTitle(title: string): string {
  return `${title} - ${APP_TITLE_SUFFIX}`
}

/** Sets `document.title` for the current route. */
export function usePageTitle(title: string): void {
  useEffect(() => {
    document.title = formatPageTitle(title)
  }, [title])
}
