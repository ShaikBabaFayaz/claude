import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createMember,
  deactivateMember,
  getMember,
  listMembers,
  reactivateMember,
  updateMember,
} from '../../api/members'
import { queryKeys } from '../../api/queryKeys'
import type { MemberFilters, MemberRequest, MemberStatus } from '../../api/types'

const STATUS_VALUES: readonly MemberStatus[] = ['ACTIVE', 'INACTIVE']

/**
 * Reads the Members screen state from the URL query string (architecture 9.3): `status`
 * (`ACTIVE` | `INACTIVE`; anything else means "All") and `page` (zero-based). Invalid values are
 * dropped rather than sent to the API, so a hand-edited URL cannot produce a 400.
 */
export function parseMemberFilters(params: URLSearchParams): MemberFilters {
  const status = params.get('status')
  const page = Number(params.get('page'))
  return {
    status: STATUS_VALUES.find((value) => value === status),
    page: Number.isInteger(page) && page > 0 ? page : undefined,
  }
}

/** Inverse of `parseMemberFilters`; "All" and page 0 are left out to keep URLs short. */
export function memberFiltersToSearchParams(filters: MemberFilters): URLSearchParams {
  const params = new URLSearchParams()
  if (filters.status) params.set('status', filters.status)
  if (filters.page) params.set('page', String(filters.page))
  return params
}

/** A page of members for the given filters. The previous page stays visible while the next one loads. */
export function useMembersList(filters: MemberFilters) {
  return useQuery({
    queryKey: queryKeys.members(filters),
    queryFn: ({ signal }) => listMembers(filters, signal),
    placeholderData: keepPreviousData,
  })
}

export function useMember(id: number) {
  return useQuery({
    queryKey: queryKeys.member(id),
    queryFn: ({ signal }) => getMember(id, signal),
  })
}

/** Every member change refreshes every cached member list and detail (architecture 9.3). */
export function useCreateMember() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (member: MemberRequest) => createMember(member),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.membersRoot })
    },
  })
}

export function useUpdateMember(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (member: MemberRequest) => updateMember(id, member),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.membersRoot })
    },
  })
}

/**
 * Deactivate and reactivate wait for the refreshed list before the mutation counts as finished,
 * so a confirmation dialog closes only once the row shows its new state (or is gone). A failed
 * call (for example 409 `MEMBER_HAS_ACTIVE_LOANS`) also refreshes the list, correcting a stale row.
 */
function useMemberStatusChange(change: (id: number) => Promise<unknown>) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.membersRoot })
  return useMutation({ mutationFn: change, onSuccess: refresh, onError: refresh })
}

export const useDeactivateMember = () => useMemberStatusChange(deactivateMember)
export const useReactivateMember = () => useMemberStatusChange(reactivateMember)
