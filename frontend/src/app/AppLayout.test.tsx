import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { createFakeBookApi } from '../test/handlers/books'
import { createFakeLoanApi } from '../test/handlers/loans'
import { createFakeMemberApi } from '../test/handlers/members'
import { renderApp } from '../test/render'
import { server } from '../test/server'

const NAV_LABELS = ['Books', 'Members', 'Issue & Return', 'Overdue']

// Several routes under test are real screens that load data; the layout tests do not care about the
// data, so the books and members APIs are served by the shared fakes instead of being left unmocked.
beforeEach(() =>
  server.use(...createFakeBookApi().handlers, ...createFakeMemberApi().handlers, ...createFakeLoanApi().handlers),
)

describe('AC-X.2 persistent navigation', () => {
  it('AC-X.2 offers Books, Members, Issue & Return and Overdue in a labelled nav landmark on every screen', () => {
    for (const route of ['/books', '/members', '/loans', '/loans/overdue', '/does-not-exist']) {
      const { unmount } = renderApp({ route })
      const nav = screen.getByRole('navigation', { name: 'Main' })
      expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(NAV_LABELS)
      unmount()
    }
  })

  it('AC-X.2 reaches every screen by clicking the navigation, without typing URLs', async () => {
    const user = userEvent.setup()
    const { router } = renderApp({ route: '/books' })
    const nav = screen.getByRole('navigation', { name: 'Main' })

    await user.click(within(nav).getByRole('link', { name: 'Members' }))
    expect(router.state.location.pathname).toBe('/members')
    await user.click(within(nav).getByRole('link', { name: 'Issue & Return' }))
    expect(router.state.location.pathname).toBe('/loans')
    await user.click(within(nav).getByRole('link', { name: 'Overdue' }))
    expect(router.state.location.pathname).toBe('/loans/overdue')
    await user.click(within(nav).getByRole('link', { name: 'Books' }))
    expect(router.state.location.pathname).toBe('/books')
  })

  it('AC-X.2 is operable by keyboard alone: Tab to a link and press Enter', async () => {
    const user = userEvent.setup()
    const { router } = renderApp({ route: '/books' })
    await user.tab() // skip link
    await user.tab() // Books
    await user.tab() // Members
    expect(screen.getByRole('link', { name: 'Members' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(router.state.location.pathname).toBe('/members')
  })

  it('AC-X.2 marks only the current page with aria-current="page"', () => {
    const cases: Array<[string, string]> = [
      ['/books', 'Books'],
      ['/books/new', 'Books'],
      ['/members', 'Members'],
      ['/loans', 'Issue & Return'],
      ['/loans/overdue', 'Overdue'],
    ]
    for (const [route, current] of cases) {
      const { unmount } = renderApp({ route })
      const nav = screen.getByRole('navigation', { name: 'Main' })
      const marked = within(nav)
        .getAllByRole('link')
        .filter((link) => link.getAttribute('aria-current') === 'page')
        .map((link) => link.textContent)
      expect(marked, route).toEqual([current])
      unmount()
    }
  })

  it('AC-X.2 redirects / to /books', () => {
    const { router } = renderApp({ route: '/' })
    expect(router.state.location.pathname).toBe('/books')
    expect(screen.getByRole('heading', { level: 1, name: 'Books' })).toBeInTheDocument()
  })
})

describe('page titles (DR-11, WCAG 2.4.2)', () => {
  const titles: Array<[string, string, string]> = [
    ['/books', 'Books', 'Books - Library'],
    ['/books/new', 'Add book', 'Add book - Library'],
    ['/books/7/edit', 'Edit book', 'Edit book - Library'],
    ['/members', 'Members', 'Members - Library'],
    ['/members/new', 'Register member', 'Register member - Library'],
    ['/members/7/edit', 'Edit member', 'Edit member - Library'],
    ['/loans', 'Issue & Return', 'Issue & Return - Library'],
    ['/loans/overdue', 'Overdue loans', 'Overdue loans - Library'],
    ['/nowhere', 'Page not found', 'Page not found - Library'],
  ]

  it.each(titles)('%s shows h1 "%s" and sets document.title "%s"', (route, heading, title) => {
    renderApp({ route })
    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
    expect(document.title).toBe(title)
  })

  it('changes document.title when navigating', async () => {
    const user = userEvent.setup()
    renderApp({ route: '/books' })
    await user.click(screen.getByRole('link', { name: 'Overdue' }))
    expect(document.title).toBe('Overdue loans - Library')
  })
})

describe('focus management (DR-11, WCAG 2.4.3)', () => {
  it('moves focus to the page h1 after navigating to another route', async () => {
    const user = userEvent.setup()
    renderApp({ route: '/books' })
    await user.click(screen.getByRole('link', { name: 'Members' }))
    const heading = screen.getByRole('heading', { level: 1, name: 'Members' })
    expect(heading).toHaveFocus()
    expect(heading).toHaveAttribute('tabindex', '-1')
  })

  it('does not steal focus on the first render', () => {
    renderApp({ route: '/members' })
    expect(screen.getByRole('heading', { level: 1 })).not.toHaveFocus()
    expect(document.body).toHaveFocus()
  })

  it('does not move focus when only the query string changes (filters and paging)', async () => {
    const { router } = renderApp({ route: '/books' })
    const heading = screen.getByRole('heading', { level: 1 })
    expect(heading).not.toHaveFocus()
    await act(() => router.navigate('/books?page=2'))
    expect(screen.getByRole('heading', { level: 1 })).not.toHaveFocus()
  })

  it('focuses the heading of the not-found page too', async () => {
    const { router } = renderApp({ route: '/books' })
    await act(() => router.navigate('/nope'))
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toHaveFocus()
  })

  it('skip link moves focus to the main region', async () => {
    const user = userEvent.setup()
    renderApp({ route: '/books' })
    await user.click(screen.getByRole('link', { name: 'Skip to main content' }))
    expect(screen.getByRole('main')).toHaveFocus()
  })
})

describe('not found page (AC-X.3)', () => {
  it('shows a friendly message and a link back to Books', async () => {
    const user = userEvent.setup()
    const { router } = renderApp({ route: '/no/such/page' })
    expect(screen.getByText('The page you are looking for does not exist.')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Back to Books' }))
    expect(router.state.location.pathname).toBe('/books')
  })
})

describe('layout structure and accessibility (NFR-3)', () => {
  it('has a banner, one main landmark and a single h1', () => {
    renderApp({ route: '/books' })
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  })

  it.each(['/books', '/books/new', '/members', '/loans', '/loans/overdue', '/nope'])(
    'has no axe violations on %s',
    async (route) => {
      const { container } = renderApp({ route })
      await expectNoA11yViolations(container)
    },
  )
})
