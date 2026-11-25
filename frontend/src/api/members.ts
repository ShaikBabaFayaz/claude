import { http } from './http'
import type { MemberFilters, MemberRequest, MemberResponse, PageResponse } from './types'

export function listMembers(filters: MemberFilters = {}, signal?: AbortSignal) {
  return http.get<PageResponse<MemberResponse>>('/members', { query: filters, signal })
}

export function getMember(id: number, signal?: AbortSignal) {
  return http.get<MemberResponse>(`/members/${id}`, { signal })
}

export function createMember(member: MemberRequest) {
  return http.post<MemberResponse>('/members', { body: member })
}

export function updateMember(id: number, member: MemberRequest) {
  return http.put<MemberResponse>(`/members/${id}`, { body: member })
}

export function deactivateMember(id: number) {
  return http.post<MemberResponse>(`/members/${id}/deactivate`)
}

export function reactivateMember(id: number) {
  return http.post<MemberResponse>(`/members/${id}/reactivate`)
}
