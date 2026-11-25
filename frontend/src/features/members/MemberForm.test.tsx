import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { apiErrorResponse, proxyDownResponse } from '../../test/handlers/common'
import { createFakeMemberApi, makeMember } from '../../test/handlers/members'
import { createTestQueryClient, renderApp } from '../../test/render'
import { server } from '../../test/server'
import { loadVectors } from '../../test/vectors'

const VALID = { name: 'Barbara Liskov', email: 'barbara@example.org' }

function field(label: string): HTMLInputElement {
  return screen.getByLabelText(new RegExp(`^${label}`)) as HTMLInputElement
}

async function fill(user: UserEvent, values: Partial<typeof VALID>) {
  for (const [name, label] of [
    ['name', 'Name'],
    ['email', 'Email'],
  ] as const) {
    const value = values[name]
    if (value === undefined) continue
    await user.clear(field(label))
    if (value !== '') await user.type(field(label), value)
  }
}

function renderRegister() {
  const api = createFakeMemberApi()
  server.use(...api.handlers)
  return { api, ...renderApp({ route: '/members/new' }) }
}

const posts = (api: ReturnType<typeof createFakeMemberApi>) => api.requests.filter((request) => request.method === 'POST')

describe('AC-3.FE.2 client-side validation', () => {
  it('AC-3.FE.2 shows an inline error next to each empty field and sends nothing to the backend', async () => {
    const user = userEvent.setup()
    const { api } = renderRegister()
    expect(document.title).toBe('Register member - Library')
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    expect(field('Name')).toBeInvalid()
    expect(field('Name')).toHaveAccessibleDescription(expect.stringContaining('Name is required.'))
    expect(field('Email')).toBeInvalid()
    expect(field('Email')).toHaveAccessibleDescription(expect.stringContaining('Email is required.'))
    expect(screen.getByRole('alert')).toHaveTextContent('The member was not saved')
    expect(field('Name')).toHaveFocus()
    expect(api.requests).toHaveLength(0)
  })

  it('AC-3.FE.2 rejects a name longer than 100 characters', async () => {
    const user = userEvent.setup()
    const { api } = renderRegister()
    await fill(user, { email: VALID.email })
    await user.click(field('Name'))
    await user.paste('n'.repeat(101))
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    expect(field('Name')).toHaveAccessibleDescription(expect.stringContaining('Name must be at most 100 characters.'))
    expect(api.requests).toHaveLength(0)
  })

  it('AC-3.FE.2 rejects every invalid email of the shared test vectors inline, without calling the backend', async () => {
    const user = userEvent.setup()
    const { api } = renderRegister()
    await fill(user, { name: VALID.name })
    const invalid = loadVectors('email').invalid.map((vector) => vector.input)
    expect(invalid.length).toBeGreaterThan(5)
    for (const input of invalid) {
      await user.clear(field('Email'))
      if (input !== '') {
        await user.click(field('Email'))
        await user.paste(input)
      }
      await user.click(screen.getByRole('button', { name: 'Register member' }))
      expect(field('Email'), JSON.stringify(input)).toBeInvalid()
    }
    expect(api.requests).toHaveLength(0)
  })

  it('AC-3.FE.2 sends the trimmed name and email', async () => {
    const user = userEvent.setup()
    const { api } = renderRegister()
    await fill(user, { name: '  Barbara Liskov ', email: ' barbara@example.org ' })
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    await waitFor(() => expect(posts(api)).toHaveLength(1))
    expect(posts(api)[0].body).toEqual(VALID)
  })

  it('AC-3.FE.2 has no axe violations on the empty form and on the form showing errors', async () => {
    const user = userEvent.setup()
    const { container } = renderRegister()
    await expectNoA11yViolations(container)
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    await expectNoA11yViolations(container)
  })
})

describe('AC-3.FE.2 server rejections', () => {
  it('AC-3.FE.2 shows a duplicate email inline on the email field (case-insensitive), keeps the values and focuses the field', async () => {
    const user = userEvent.setup()
    const { container } = renderRegister()
    await fill(user, { name: VALID.name, email: 'ADA@Example.org' })
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    await waitFor(() =>
      expect(field('Email')).toHaveAccessibleDescription(
        expect.stringContaining('A member with this email address already exists.'),
      ),
    )
    expect(field('Email')).toBeInvalid()
    expect(field('Email')).toHaveFocus()
    expect(field('Email')).toHaveValue('ADA@Example.org')
    expect(field('Name')).toHaveValue(VALID.name)
    expect(screen.getByRole('button', { name: 'Register member' })).toBeEnabled()
    await expectNoA11yViolations(container)
  })

  it('AC-3.FE.2 applies server validation errors on both fields, focusing the first in page order', async () => {
    const user = userEvent.setup()
    renderRegister()
    server.use(
      http.post('/api/members', () =>
        apiErrorResponse(400, 'VALIDATION_FAILED', 'Invalid.', [
          { field: 'email', message: 'Email is not valid.' },
          { field: 'name', message: 'Name must not be blank.' },
        ]),
      ),
    )
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    await waitFor(() => expect(field('Name')).toBeInvalid())
    expect(field('Email')).toHaveAccessibleDescription(expect.stringContaining('Email is not valid.'))
    expect(field('Name')).toHaveFocus()
    expect(field('Email')).toHaveValue(VALID.email)
  })

  it('AC-3.FE.2 AC-X.3 shows a field-less failure as a friendly form-level message and keeps the values', async () => {
    const user = userEvent.setup()
    renderRegister()
    server.use(http.post('/api/members', () => proxyDownResponse(), { once: true }))
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The library server cannot be reached.')
    expect(field('Name')).toHaveValue(VALID.name)
    expect(field('Email')).toHaveValue(VALID.email)
  })
})

describe('AC-3.FE.2 successful register and edit', () => {
  it('AC-3.FE.2 register navigates to the list, which shows the new member, with a success notice', async () => {
    const user = userEvent.setup()
    const { router, api } = renderRegister()
    await fill(user, VALID)
    await user.click(screen.getByRole('button', { name: 'Register member' }))
    expect(await screen.findByRole('table', { name: 'Library members' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/members')
    expect(screen.getByText(/"Barbara Liskov" was registered\./)).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: 'Barbara Liskov' })).toBeInTheDocument()
    expect(api.members.at(-1)).toMatchObject({ name: 'Barbara Liskov', status: 'ACTIVE' })
  })

  it('AC-3.FE.2 submitting twice quickly sends one request', async () => {
    const user = userEvent.setup()
    renderRegister()
    let calls = 0
    server.use(
      http.post('/api/members', async () => {
        calls += 1
        await delay(50)
        return HttpResponse.json(makeMember({ id: 99, ...VALID }), { status: 201 })
      }),
    )
    await fill(user, VALID)
    await user.dblClick(screen.getByRole('button', { name: 'Register member' }))
    await screen.findByRole('table', { name: 'Library members' })
    expect(calls).toBe(1)
  })

  it('AC-3.FE.2 edit loads the member, saves with PUT and shows a success notice; the own email is accepted', async () => {
    const user = userEvent.setup()
    const api = createFakeMemberApi()
    server.use(...api.handlers)
    const { router } = renderApp({ route: '/members/1/edit' })
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(document.title).toBe('Edit member - Library')
    expect(field('Name')).toHaveValue('Ada Lovelace')
    expect(field('Email')).toHaveValue('ada@example.org')

    await fill(user, { name: 'Ada King' })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('table', { name: 'Library members' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/members')
    expect(screen.getByText(/"Ada King" was saved\./)).toBeInTheDocument()
    expect(await screen.findByRole('cell', { name: 'Ada King' })).toBeInTheDocument()
    expect(api.requests.filter((request) => request.method === 'PUT')).toHaveLength(1)
  })

  it('AC-3.FE.2 editing to another member\'s email is reported inline and keeps the entered values', async () => {
    const user = userEvent.setup()
    const api = createFakeMemberApi()
    server.use(...api.handlers)
    renderApp({ route: '/members/1/edit' })
    await screen.findByRole('button', { name: 'Save changes' })
    await fill(user, { email: 'Alan@example.org' })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(field('Email')).toBeInvalid())
    expect(field('Email')).toHaveValue('Alan@example.org')
    expect(field('Email')).toHaveFocus()
  })

  it('AC-3.FE.2 the Cancel link returns to the list without saving', async () => {
    const user = userEvent.setup()
    const { router, api } = renderRegister()
    await user.click(screen.getByRole('link', { name: 'Cancel' }))
    expect(router.state.location.pathname).toBe('/members')
    expect(api.requests.filter((request) => request.method !== 'GET')).toHaveLength(0)
  })
})

describe('member edit page loading and failure (AC-X.3, DR-19)', () => {
  it('shows a loading indicator, then the form', async () => {
    const api = createFakeMemberApi()
    server.use(...api.handlers)
    renderApp({ route: '/members/2/edit' })
    expect(screen.getByText('Loading member…').closest('[role="status"]')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
  })

  it('shows "not found" with a link back and no Retry for an unknown member, without retrying the request', async () => {
    const user = userEvent.setup()
    const api = createFakeMemberApi()
    server.use(...api.handlers)
    const { router, container } = renderApp({
      route: '/members/999/edit',
      queryClient: createTestQueryClient({ appRetryPolicy: true }),
    })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This member no longer exists.')
    expect(within(alert).queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
    expect(api.requests.filter((request) => request.url === '/api/members/999')).toHaveLength(1)
    await expectNoA11yViolations(container)
    await user.click(within(alert).getByRole('link', { name: 'Back to Members' }))
    expect(router.state.location.pathname).toBe('/members')
  })

  it('treats a non-numeric id as not found without calling the API', async () => {
    const api = createFakeMemberApi()
    server.use(...api.handlers)
    renderApp({ route: '/members/abc/edit' })
    expect(await screen.findByRole('alert')).toHaveTextContent('could not be found')
    expect(api.requests).toHaveLength(0)
  })

  it('offers Retry when the server is unreachable and loads the form after it recovers', async () => {
    const user = userEvent.setup()
    const api = createFakeMemberApi()
    server.use(http.get('/api/members/1', () => proxyDownResponse(), { once: true }), ...api.handlers)
    renderApp({ route: '/members/1/edit' })
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The library server cannot be reached.')
    await user.click(within(alert).getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument()
    expect(field('Name')).toHaveValue('Ada Lovelace')
  })
})
