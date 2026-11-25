import { useState } from 'react'
import type { MemberResponse } from '../../api/types'
import { useDebouncedValue } from '../../lib/useDebouncedValue'
import { useMembersList } from '../members/useMemberQueries'
import { SearchPicker } from './SearchPicker'

/** Longest `search` value the members API accepts. */
export const MEMBER_SEARCH_MAX_LENGTH = 254

interface MemberPickerProps {
  selected: MemberResponse | null
  onSelect: (member: MemberResponse) => void
  searchLabel?: string
  legend?: string
}

/** Offers only ACTIVE members, searched by name or email (AC-4.FE.1, ADR-19). */
export function MemberPicker({
  selected,
  onSelect,
  searchLabel = 'Find a member',
  legend = 'Matching members',
}: MemberPickerProps) {
  const [search, setSearch] = useState('')
  const debounced = useDebouncedValue(search.trim())
  const query = useMembersList({ status: 'ACTIVE', search: debounced || undefined })

  return (
    <SearchPicker
      searchLabel={searchLabel}
      legend={legend}
      noun={{ singular: 'member', plural: 'members' }}
      search={search}
      onSearchChange={setSearch}
      maxSearchLength={MEMBER_SEARCH_MAX_LENGTH}
      query={query}
      selected={selected}
      onSelect={onSelect}
      renderItem={(member) => (
        <>
          <span>{member.name}</span>
          <span>{member.email}</span>
        </>
      )}
      selectedText={(member) => member.name}
      emptyText="No active member matches your search."
    />
  )
}
