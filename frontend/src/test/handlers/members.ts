import { http, HttpResponse, type RequestHandler } from 'msw'
import type { MemberRequest, MemberResponse, MemberStatus } from '../../api/types'
import { apiErrorResponse, pageOf, readPageQuery, recordedUrl, type RecordedRequest } from './common'

/** A member as the backend returns it (architecture 6.1). */
export function makeMember(overrides: Partial<MemberResponse> = {}): MemberResponse {
  return {
    id: 1,
    name: 'Ada Lovelace',
    email: 'ada@example.org',
    status: 'ACTIVE',
    activeLoanCount: 0,
    ...overrides,
  }
}

/** Active member with a loan, active without, inactive, and one at the 3-loan limit. */
export function sampleMembers(): MemberResponse[] {
  return [
    makeMember({ id: 1, name: 'Ada Lovelace', email: 'ada@example.org', status: 'ACTIVE', activeLoanCount: 1 }),
    makeMember({ id: 2, name: 'Alan Turing', email: 'alan@example.org', status: 'ACTIVE', activeLoanCount: 0 }),
    makeMember({ id: 3, name: 'Grace Hopper', email: 'grace@example.org', status: 'INACTIVE', activeLoanCount: 0 }),
    makeMember({ id: 4, name: 'Linus Torvalds', email: 'linus@example.org', status: 'ACTIVE', activeLoanCount: 3 }),
  ]
}

/** `count` generated members `Member 001`, ... alternating Active / Inactive, for pagination tests. */
export function manyMembers(count: number): MemberResponse[] {
  const members: MemberResponse[] = []
  for (let i = 1; i <= count; i++) {
    const label = String(i).padStart(3, '0')
    members.push(
      makeMember({
        id: i,
        name: `Member ${label}`,
        email: `member${label}@example.org`,
        status: i % 2 === 0 ? 'INACTIVE' : 'ACTIVE',
        activeLoanCount: 0,
      }),
    )
  }
  return members
}

function includesIgnoreCase(haystack: string, needle: string): boolean {
  return haystack.toLowerCase().includes(needle.toLowerCase())
}

export interface FakeMemberApi {
  members: MemberResponse[]
  requests: RecordedRequest[]
  handlers: RequestHandler[]
}

/**
 * In-memory stand-in for `/api/members` following architecture 6.3: `status` and `search`
 * (name or email contains, case-insensitive; D9), order `lower(name)` then id, 20 per page,
 * case-insensitive duplicate email -> 409 with a field error, deactivate with active loans -> 409
 * `MEMBER_HAS_ACTIVE_LOANS`, deactivate/reactivate idempotent. Register with `server.use(...api.handlers)`.
 */
export function createFakeMemberApi(initial: MemberResponse[] = sampleMembers()): FakeMemberApi {
  const members = initial.map((member) => ({ ...member }))
  const requests: RecordedRequest[] = []
  let nextId = members.reduce((max, member) => Math.max(max, member.id), 0) + 1

  const findMember = (id: string | readonly string[] | undefined) =>
    members.find((member) => String(member.id) === String(id))
  const notFound = () => apiErrorResponse(404, 'MEMBER_NOT_FOUND', 'No member with this id exists.')
  const emailTaken = (email: string, exceptId?: number) =>
    members.some((member) => member.id !== exceptId && member.email.toLowerCase() === email.toLowerCase())
  const duplicateEmail = () =>
    apiErrorResponse(409, 'EMAIL_ALREADY_EXISTS', 'A member with this email already exists.', [
      { field: 'email', message: 'A member with this email address already exists.' },
    ])
  const setStatus = (member: MemberResponse, status: MemberStatus) => {
    member.status = status
    return HttpResponse.json(member)
  }

  const handlers: RequestHandler[] = [
    http.get('/api/members', ({ request }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const url = new URL(request.url)
      const status = url.searchParams.get('status')
      const search = url.searchParams.get('search')
      const matches = members
        .filter((member) => !status || member.status === status)
        .filter((member) => !search || includesIgnoreCase(member.name, search) || includesIgnoreCase(member.email, search))
        .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.id - b.id)
      const { page, size } = readPageQuery(url)
      return HttpResponse.json(pageOf(matches, page, size))
    }),

    http.get('/api/members/:id', ({ request, params }) => {
      requests.push({ method: 'GET', url: recordedUrl(request) })
      const member = findMember(params.id)
      return member ? HttpResponse.json(member) : notFound()
    }),

    http.post('/api/members', async ({ request }) => {
      const body = (await request.json()) as MemberRequest
      requests.push({ method: 'POST', url: recordedUrl(request), body })
      if (emailTaken(body.email)) return duplicateEmail()
      const member: MemberResponse = {
        id: nextId++,
        name: body.name,
        email: body.email,
        status: 'ACTIVE',
        activeLoanCount: 0,
      }
      members.push(member)
      return HttpResponse.json(member, { status: 201, headers: { Location: `/api/members/${member.id}` } })
    }),

    http.put('/api/members/:id', async ({ request, params }) => {
      const body = (await request.json()) as MemberRequest
      requests.push({ method: 'PUT', url: recordedUrl(request), body })
      const member = findMember(params.id)
      if (!member) return notFound()
      if (emailTaken(body.email, member.id)) return duplicateEmail()
      Object.assign(member, { name: body.name, email: body.email })
      return HttpResponse.json(member)
    }),

    http.post('/api/members/:id/deactivate', ({ request, params }) => {
      requests.push({ method: 'POST', url: recordedUrl(request) })
      const member = findMember(params.id)
      if (!member) return notFound()
      if (member.status === 'INACTIVE') return HttpResponse.json(member) // idempotent (D9)
      if (member.activeLoanCount > 0) {
        return apiErrorResponse(409, 'MEMBER_HAS_ACTIVE_LOANS', 'The member has active loans and cannot be deactivated.')
      }
      return setStatus(member, 'INACTIVE')
    }),

    http.post('/api/members/:id/reactivate', ({ request, params }) => {
      requests.push({ method: 'POST', url: recordedUrl(request) })
      const member = findMember(params.id)
      return member ? setStatus(member, 'ACTIVE') : notFound() // idempotent (D9)
    }),
  ]

  return { members, requests, handlers }
}
