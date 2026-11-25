import { useMemo, useState, type ChangeEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import type { MemberFilters, MemberResponse, MemberStatus } from '../../api/types'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { FormField } from '../../components/FormField'
import { LoadingState } from '../../components/LoadingState'
import { useNotifications } from '../../components/NotificationProvider'
import { Pagination } from '../../components/Pagination'
import { getErrorMessage } from '../../lib/errorMessages'
import { usePageTitle } from '../../lib/usePageTitle'
import { MemberTable } from './MemberTable'
import styles from './MembersPage.module.css'
import {
  memberFiltersToSearchParams,
  parseMemberFilters,
  useDeactivateMember,
  useMembersList,
  useReactivateMember,
} from './useMemberQueries'

const STATUS_WORD: Record<MemberStatus, string> = { ACTIVE: 'active', INACTIVE: 'inactive' }

function matchCountText(totalElements: number): string {
  return `${totalElements} ${totalElements === 1 ? 'member' : 'members'} found`
}

/**
 * Members screen (architecture 9.2): status filter (All / Active / Inactive) and page in the URL,
 * match count in a live region, table with edit and deactivate/reactivate, and empty, loading and
 * error states (AC-3.FE.1, 3, 4, 5, 6). Deactivating asks for confirmation (NFR-8).
 */
export function MembersPage() {
  usePageTitle('Members')
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => parseMemberFilters(searchParams), [searchParams])
  const query = useMembersList(filters)
  const { notify } = useNotifications()
  const deactivate = useDeactivateMember()
  const reactivate = useReactivateMember()
  const [deactivateTarget, setDeactivateTarget] = useState<MemberResponse | null>(null)

  function applyFilters(next: MemberFilters) {
    setSearchParams(memberFiltersToSearchParams(next))
  }

  function changeStatus(event: ChangeEvent<HTMLSelectElement>) {
    const status = event.target.value as MemberStatus | ''
    applyFilters({ status: status || undefined }) // a new filter starts at the first page
  }

  function confirmDeactivate() {
    if (!deactivateTarget) return
    const member = deactivateTarget
    deactivate.mutate(member.id, {
      onSuccess: () => {
        setDeactivateTarget(null)
        // The button that opened the dialog is replaced by "Reactivate" (or the row left an
        // "Active" list), so focus goes to the notice instead of falling back to <body> (DR-11).
        notify(`"${member.name}" was deactivated.`, { focus: true })
      },
      onError: (error) => {
        setDeactivateTarget(null)
        notify(getErrorMessage(error), { kind: 'error', focus: true })
      },
    })
  }

  function reactivateMember(member: MemberResponse) {
    reactivate.mutate(member.id, {
      onSuccess: () => notify(`"${member.name}" was reactivated.`, { focus: true }),
      onError: (error) => notify(getErrorMessage(error), { kind: 'error', focus: true }),
    })
  }

  const data = query.data
  const noResults = !query.isError && data !== undefined && data.totalElements === 0
  const isEmptyList = noResults && !filters.status && !query.isPlaceholderData
  const isNoMatch = noResults && Boolean(filters.status) && !query.isPlaceholderData

  let content: ReactNode
  if (query.isError) {
    content = <ErrorState error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />
  } else if (query.isPending || (noResults && query.isPlaceholderData)) {
    content = <LoadingState label="Loading members…" />
  } else if (isNoMatch && filters.status) {
    content = (
      <EmptyState
        title={`No ${STATUS_WORD[filters.status]} members`}
        action={
          <button type="button" className="button button--primary" onClick={() => applyFilters({})}>
            Show all members
          </button>
        }
      >
        No member has this status.
      </EmptyState>
    )
  } else if (isEmptyList) {
    content = (
      <EmptyState
        title="No members registered yet"
        action={
          <Link to="/members/new" className="button button--primary">
            Register member
          </Link>
        }
      >
        Register the first member so books can be issued.
      </EmptyState>
    )
  } else if (query.data.content.length === 0) {
    // A page number beyond the last page, for example from an old link.
    content = (
      <EmptyState
        title="There are no members on this page"
        action={
          <button
            type="button"
            className="button button--primary"
            onClick={() => applyFilters({ status: filters.status })}
          >
            Go to the first page
          </button>
        }
      >
        The list has fewer pages than this address asks for.
      </EmptyState>
    )
  } else {
    content = (
      <div aria-busy={query.isPlaceholderData} className={query.isPlaceholderData ? styles.stale : undefined}>
        <MemberTable
          members={query.data.content}
          onDeactivate={setDeactivateTarget}
          onReactivate={reactivateMember}
          busyMemberId={reactivate.isPending ? reactivate.variables : undefined}
        />
        <Pagination
          page={query.data.page}
          totalPages={query.data.totalPages}
          onPageChange={(page) => applyFilters({ ...filters, page: page > 0 ? page : undefined })}
        />
      </div>
    )
  }

  return (
    <>
      <div className={styles.header}>
        <h1 tabIndex={-1}>Members</h1>
        {!isEmptyList && (
          <Link to="/members/new" className="button button--primary">
            Register member
          </Link>
        )}
      </div>
      <div className={styles.filter}>
        <FormField label="Status">
          {(control) => (
            <select className="input" value={filters.status ?? ''} onChange={changeStatus} {...control}>
              <option value="">All</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          )}
        </FormField>
      </div>
      {/* Always rendered so screen readers announce the new count after the filter changes (DR-11). */}
      <p role="status" className={styles.count}>
        {data && !query.isError ? matchCountText(data.totalElements) : ''}
      </p>
      {content}
      <ConfirmDialog
        open={deactivateTarget !== null}
        title={deactivateTarget ? `Deactivate "${deactivateTarget.name}"?` : 'Deactivate member?'}
        confirmLabel="Deactivate member"
        danger
        busy={deactivate.isPending}
        onCancel={() => setDeactivateTarget(null)}
        onConfirm={confirmDeactivate}
      >
        An inactive member cannot borrow books until they are reactivated. A member who still has books on loan
        cannot be deactivated.
      </ConfirmDialog>
    </>
  )
}
