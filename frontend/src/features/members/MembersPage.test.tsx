import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { proxyDownResponse } from '../../test/handlers/common'
import { createFakeMemberApi, makeMember, manyMembers, sampleMembers } from '../../test/handlers/members'
import { createTestQueryClient, renderApp } from '../../test/render'
import { server } from '../../test/server'

const FRIENDLY_UNAVAILABLE = 'The library server cannot be reached. Check that it is running, then try again.'

const statusSelect = () => screen.getByLabelText('Status') as HTMLSelectElement
const count = () => screen.getByText(/\d+ members? found$/)

function renderMembers(members = sampleMembers(), route = '/members') {
  const api = createFakeMemberApi(members)
  server.use(...api.handlers)
  return { api, ...renderApp({ route }) }
}

function rowFor(name: string): HTMLTableRowElement {
  return screen.getByRole('cell', { name }).closest('tr') as HTMLTableRowElement
}

function names(): string[] {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[0].textContent ?? '')
}

const writes = (api: { requests: Array<{ method: string; url: string }> }) => api.requests.filter((request) => request.method !== 'GET')

describe('AC-3.FE.1 members list', () => {
  it('AC-3.FE.1 shows name, email and an Active / Inactive status label with edit and deactivate/reactivate actions', async () => {
    renderMembers()
    expect(screen.getByRole('heading', { level: 1, name: 'Members' })).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: 'Library members' })
    expect(within(table).getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      'Name',
      'Email',
      'Status',
      'Actions',
    ])
    expect(names()).toEqual(['Ada Lovelace', 'Alan Turing', 'Grace Hopper', 'Linus Torvalds'])

    const ada = within(rowFor('Ada Lovelace'))
    expect(ada.getAllByRole('cell').map((cell) => cell.textContent).slice(0, 3)).toEqual([
      'Ada Lovelace',
      'ada@example.org',
      expect.stringContaining('Active'),
    ])
    expect(ada.getByRole('link', { name: 'Edit Ada Lovelace' })).toHaveAttribute('href', '/members/1/edit')
    expect(ada.getByRole('button', { name: 'Deactivate Ada Lovelace' })).toBeInTheDocument()
    expect(ada.queryByRole('button', { name: /Reactivate/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Register member' })).toHaveAttribute('href', '/members/new')
    expect(document.title).toBe('Members - Library')
  })

  it('AC-3.FE.1 conveys the status by text and by a different shape, not by colour alone', async () => {
    renderMembers()
    await screen.findByRole('table')
    const active = within(rowFor('Ada Lovelace')).getByText('Active')
    const inactive = within(rowFor('Grace Hopper')).getByText('Inactive')
    const shape = (badge: HTMLElement) => badge.querySelector('[aria-hidden="true"]')?.textContent
    expect(shape(active)).toBeTruthy()
    expect(shape(inactive)).toBeTruthy()
    expect(shape(active)).not.toBe(shape(inactive))
  })

  it('AC-3.FE.1 paginates, announces the match count in a live region and keeps the status filter when changing page', async () => {
    const user = userEvent.setup()
    const { api, router } = renderMembers(manyMembers(45))
    await screen.findByRole('table')
    expect(count()).toHaveTextContent('45 members found')
    expect(count().closest('[role="status"]')).toBe(count())
    expect(names()).toHaveLength(20)
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(names()[0]).toBe('Member 021'))
    expect(router.state.location.search).toBe('?page=1')

    await user.selectOptions(statusSelect(), 'ACTIVE') // 23 active members, back to page 1
    await waitFor(() => expect(count()).toHaveTextContent('23 members found'))
    expect(router.state.location.search).toBe('?status=ACTIVE')
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(names()).toEqual(['Member 041', 'Member 043', 'Member 045']))
    expect(router.state.location.search).toBe('?status=ACTIVE&page=1')
    expect(api.requests.at(-1)?.url).toBe('/api/members?status=ACTIVE&page=1')
  })

  it('AC-3.FE.1 filters by status through the URL: Active, Inactive and All', async () => {
    const user = userEvent.setup()
    const { router } = renderMembers()
    await screen.findByRole('table')
    expect(Array.from(statusSelect().options).map((option) => option.text)).toEqual(['All', 'Active', 'Inactive'])

    await user.selectOptions(statusSelect(), 'INACTIVE')
    await waitFor(() => expect(names()).toEqual(['Grace Hopper']))
    expect(router.state.location.search).toBe('?status=INACTIVE')

    await user.selectOptions(statusSelect(), 'ACTIVE')
    await waitFor(() => expect(names()).toEqual(['Ada Lovelace', 'Alan Turing', 'Linus Torvalds']))

    await user.selectOptions(statusSelect(), '')
    await waitFor(() => expect(names()).toHaveLength(4))
    expect(router.state.location.search).toBe('')
  })

  it('AC-3.FE.1 restores the status filter from the URL and Back returns to the previous filter', async () => {
    const user = userEvent.setup()
    const { router } = renderMembers(sampleMembers(), '/members?status=INACTIVE')
    await waitFor(() => expect(names()).toEqual(['Grace Hopper']))
    expect(statusSelect()).toHaveValue('INACTIVE')
    await user.selectOptions(statusSelect(), 'ACTIVE')
    await waitFor(() => expect(names()).toHaveLength(3))
    await act(() => router.navigate(-1))
    await waitFor(() => expect(names()).toEqual(['Grace Hopper']))
    expect(statusSelect()).toHaveValue('INACTIVE')
  })

  it('AC-3.FE.1 ignores a status the API would reject in the URL', async () => {
    const { api } = renderMembers(sampleMembers(), '/members?status=bogus&page=-1')
    await screen.findByRole('table')
    expect(api.requests[0].url).toBe('/api/members')
    expect(statusSelect()).toHaveValue('')
  })

  it('AC-3.FE.1 has no axe violations on the list', async () => {
    const { container } = renderMembers()
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })

  it('AC-3.FE.1 has no axe violations on a filtered, paginated list', async () => {
    const { container } = renderMembers(manyMembers(45), '/members?status=ACTIVE&page=1')
    await screen.findByRole('table')
    await expectNoA11yViolations(container)
  })

  it('AC-3.FE.1 the Edit action opens the edit page of that member', async () => {
    const user = userEvent.setup()
    const { router } = renderMembers()
    await user.click(await screen.findByRole('link', { name: 'Edit Alan Turing' }))
    expect(router.state.location.pathname).toBe('/members/2/edit')
    expect(await screen.findByRole('heading', { level: 1, name: 'Edit member' })).toBeInTheDocument()
  })

  it('AC-3.FE.1 offers a way back when the page in the URL is beyond the last page', async () => {
    const user = userEvent.setup()
    const { router } = renderMembers(manyMembers(25), '/members?status=ACTIVE&page=9')
    expect(await screen.findByRole('heading', { level: 2, name: 'There are no members on this page' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Go to the first page' }))
    await screen.findByRole('table')
    expect(router.state.location.search).toBe('?status=ACTIVE')
  })
})

describe('AC-3.FE.3 deactivate with confirmation', () => {
  it('AC-3.FE.3 asks first, and cancelling changes nothing', async () => {
    const user = userEvent.setup()
    const { api, container } = renderMembers()
    await screen.findByRole('table')
    const trigger = within(rowFor('Alan Turing')).getByRole('button', { name: 'Deactivate Alan Turing' })
    await user.click(trigger)

    const dialog = screen.getByRole('dialog', { name: 'Deactivate "Alan Turing"?' })
    expect(dialog).toHaveAccessibleDescription(/cannot borrow books/)
    await expectNoA11yViolations(container)

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(writes(api)).toHaveLength(0)
    expect(within(rowFor('Alan Turing')).getByText('Active')).toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('AC-3.FE.3 deactivates after confirmation: the member is shown as Inactive, with a notice that receives focus', async () => {
    const user = userEvent.setup()
    const { api } = renderMembers()
    await screen.findByRole('table')
    await user.click(within(rowFor('Alan Turing')).getByRole('button', { name: 'Deactivate Alan Turing' }))
    expect(writes(api)).toHaveLength(0)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate member' }))

    await waitFor(() => expect(within(rowFor('Alan Turing')).getByText('Inactive')).toBeInTheDocument())
    expect(writes(api).map((request) => `${request.method} ${request.url}`)).toEqual(['POST /api/members/2/deactivate'])
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    const notice = (await screen.findByText(/"Alan Turing" was deactivated\./)).closest('[role="status"]')
    expect(notice).toHaveFocus()
    // The row now offers Reactivate instead (AC-3.FE.5).
    expect(within(rowFor('Alan Turing')).getByRole('button', { name: 'Reactivate Alan Turing' })).toBeInTheDocument()
  })

  it('AC-3.FE.3 in the Active list the deactivated member leaves the list and focus does not fall back to <body>', async () => {
    const user = userEvent.setup()
    renderMembers(sampleMembers(), '/members?status=ACTIVE')
    await screen.findByRole('table')
    await user.click(within(rowFor('Alan Turing')).getByRole('button', { name: 'Deactivate Alan Turing' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate member' }))
    await waitFor(() => expect(screen.queryByRole('cell', { name: 'Alan Turing' })).not.toBeInTheDocument())
    const notice = (await screen.findByText(/"Alan Turing" was deactivated\./)).closest('[role="status"]')
    expect(notice).toHaveFocus()
  })

  it('AC-3.FE.3 the Esc key (dialog cancel event) cancels without deactivating', async () => {
    const user = userEvent.setup()
    const { api } = renderMembers()
    await screen.findByRole('table')
    await user.click(within(rowFor('Alan Turing')).getByRole('button', { name: 'Deactivate Alan Turing' }))
    // jsdom does not turn Escape into a dialog "cancel" event (see ConfirmDialog.test).
    screen.getByRole('dialog').dispatchEvent(new Event('cancel', { cancelable: true }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(writes(api)).toHaveLength(0)
  })

  it('AC-3.FE.3 AC-X.3 reports an unreachable server while deactivating with a friendly message and keeps the member Active', async () => {
    const user = userEvent.setup()
    renderMembers()
    server.use(http.post('/api/members/:id/deactivate', () => proxyDownResponse()))
    await screen.findByRole('table')
    await user.click(within(rowFor('Alan Turing')).getByRole('button', { name: 'Deactivate Alan Turing' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate member' }))
    expect(await screen.findByText(FRIENDLY_UNAVAILABLE)).toBeInTheDocument()
    expect(within(rowFor('Alan Turing')).getByText('Active')).toBeInTheDocument()
  })
})

describe('AC-3.FE.4 deactivation refused because of active loans', () => {
  it('AC-3.FE.4 explains the reason and the member remains Active', async () => {
    const user = userEvent.setup()
    const { api } = renderMembers()
    await screen.findByRole('table')
    await user.click(within(rowFor('Ada Lovelace')).getByRole('button', { name: 'Deactivate Ada Lovelace' })) // has 1 loan
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Deactivate member' }))

    const notice = await screen.findByText(/cannot be deactivated while they have books on loan/)
    expect(notice).toHaveTextContent('Return the books first.')
    expect(notice.closest('[role="status"]')).toHaveFocus()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(within(rowFor('Ada Lovelace')).getByText('Active')).toBeInTheDocument()
    expect(within(rowFor('Ada Lovelace')).getByRole('button', { name: 'Deactivate Ada Lovelace' })).toBeInTheDocument()
    expect(api.members.find((member) => member.id === 1)?.status).toBe('ACTIVE')
  })
})

describe('AC-3.FE.5 reactivate', () => {
  it('AC-3.FE.5 offers Reactivate (and no Deactivate) for an inactive member and reactivates without a dialog', async () => {
    const user = userEvent.setup()
    const { api } = renderMembers()
    await screen.findByRole('table')
    const grace = within(rowFor('Grace Hopper'))
    expect(grace.queryByRole('button', { name: /Deactivate/ })).not.toBeInTheDocument()
    await user.click(grace.getByRole('button', { name: 'Reactivate Grace Hopper' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(within(rowFor('Grace Hopper')).getByText('Active')).toBeInTheDocument())
    expect(writes(api).map((request) => `${request.method} ${request.url}`)).toEqual(['POST /api/members/3/reactivate'])
    expect((await screen.findByText(/"Grace Hopper" was reactivated\./)).closest('[role="status"]')).toHaveFocus()
    expect(within(rowFor('Grace Hopper')).getByRole('button', { name: 'Deactivate Grace Hopper' })).toBeInTheDocument()
  })

  it('AC-3.FE.5 disables the button while the request runs so a double click sends one request', async () => {
    const user = userEvent.setup()
    renderMembers()
    let calls = 0
    server.use(
      http.post('/api/members/:id/reactivate', async () => {
        calls += 1
        await delay(50)
        return HttpResponse.json(makeMember({ id: 3, name: 'Grace Hopper', email: 'grace@example.org' }))
      }),
    )
    await screen.findByRole('table')
    await user.dblClick(within(rowFor('Grace Hopper')).getByRole('button', { name: 'Reactivate Grace Hopper' }))
    await screen.findByText(/"Grace Hopper" was reactivated\./)
    expect(calls).toBe(1)
  })

  it('AC-3.FE.5 a member that disappeared meanwhile is reported', async () => {
    const user = userEvent.setup()
    const { api } = renderMembers()
    await screen.findByRole('table')
    api.members.splice(2, 1)
    await user.click(within(rowFor('Grace Hopper')).getByRole('button', { name: 'Reactivate Grace Hopper' }))
    expect(await screen.findByText(/This member no longer exists\./)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('cell', { name: 'Grace Hopper' })).not.toBeInTheDocument())
  })
})

describe('AC-3.FE.6 empty, loading and error states', () => {
  it('AC-3.FE.6 shows an empty-state message with a "Register member" action when there are no members', async () => {
    const user = userEvent.setup()
    const { router, container } = renderMembers([])
    expect(await screen.findByRole('heading', { level: 2, name: 'No members registered yet' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('link', { name: 'Register member' }))
    expect(router.state.location.pathname).toBe('/members/new')
  })

  it('AC-3.FE.6 tells "no member with this status" from an empty list and offers to show all', async () => {
    const user = userEvent.setup()
    renderMembers(sampleMembers().filter((member) => member.status === 'ACTIVE'), '/members?status=INACTIVE')
    expect(await screen.findByRole('heading', { level: 2, name: 'No inactive members' })).toBeInTheDocument()
    expect(count()).toHaveTextContent('0 members found')
    await user.click(screen.getByRole('button', { name: 'Show all members' }))
    await waitFor(() => expect(names()).toHaveLength(3))
    expect(statusSelect()).toHaveValue('')
  })

  it('AC-3.FE.6 shows a loading indicator with text while the list is fetched', async () => {
    const api = createFakeMemberApi()
    server.use(
      http.get('/api/members', async () => {
        await delay(50)
        return HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      }),
      ...api.handlers,
    )
    renderApp({ route: '/members' })
    expect(screen.getByText('Loading members…').closest('[role="status"]')).toBeInTheDocument()
    await screen.findByRole('heading', { level: 2, name: 'No members registered yet' })
  })

  it('AC-3.FE.6 AC-X.3 shows a friendly error with Retry when the server answers 500 with text, and Retry recovers', async () => {
    const user = userEvent.setup()
    const api = createFakeMemberApi()
    server.use(
      http.get('/api/members', () => new HttpResponse('Internal Server Error', { status: 500 }), { once: true }),
      ...api.handlers,
    )
    const { container } = renderApp({ route: '/members' })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(FRIENDLY_UNAVAILABLE)
    expect(alert).not.toHaveTextContent('Internal Server Error')
    await expectNoA11yViolations(container)
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('AC-3.FE.6 AC-X.3 shows the friendly message for a 502 with an empty body, after one automatic retry (DR-19)', async () => {
    let calls = 0
    server.use(
      http.get('/api/members', () => {
        calls += 1
        return proxyDownResponse()
      }),
    )
    renderApp({ route: '/members', queryClient: createTestQueryClient({ appRetryPolicy: true }) })
    expect(await screen.findByRole('alert')).toHaveTextContent(FRIENDLY_UNAVAILABLE)
    expect(calls).toBe(2)
  })
})

describe('NFR-6 member data is rendered as text', () => {
  const SCRIPT_NAME = '<script>alert(1)</script>'
  const IMG_EMAIL = 'x@<img src=x onerror=alert(2)>.org'

  it('NFR-6 AC-3.FE.1 shows a <script> name and an <img onerror> email literally, in the list and in the dialog', async () => {
    const user = userEvent.setup()
    const { container } = renderMembers([makeMember({ id: 1, name: SCRIPT_NAME, email: IMG_EMAIL })])
    const cell = await screen.findByRole('cell', { name: SCRIPT_NAME })
    expect(cell.textContent).toBe(SCRIPT_NAME)
    expect(screen.getByRole('cell', { name: IMG_EMAIL })).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    await expectNoA11yViolations(container)

    await user.click(screen.getByRole('button', { name: `Deactivate ${SCRIPT_NAME}` }))
    const dialog = screen.getByRole('dialog', { name: `Deactivate "${SCRIPT_NAME}"?` })
    expect(dialog.querySelector('script')).toBeNull()
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate member' }))
    expect(await screen.findByText(`"${SCRIPT_NAME}" was deactivated.`)).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
  })
})
