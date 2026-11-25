import { Link, useNavigate, useParams } from 'react-router'
import { isApiError } from '../../api/http'
import type { MemberRequest, MemberResponse } from '../../api/types'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import { useNotifications } from '../../components/NotificationProvider'
import { usePageTitle } from '../../lib/usePageTitle'
import { MemberForm, type MemberFormValues } from './MemberForm'
import { useCreateMember, useMember, useUpdateMember } from './useMemberQueries'

interface MemberFormPageProps {
  mode: 'create' | 'edit'
}

/** `/members/new` and `/members/:id/edit` (architecture 9.2). */
export function MemberFormPage({ mode }: MemberFormPageProps) {
  return mode === 'create' ? <RegisterMemberPage /> : <EditMemberPage />
}

function RegisterMemberPage() {
  usePageTitle('Register member')
  const navigate = useNavigate()
  const { notify } = useNotifications()
  const createMember = useCreateMember()

  async function submit(request: MemberRequest) {
    const created = await createMember.mutateAsync(request)
    notify(`"${created.name}" was registered.`)
    await navigate('/members')
  }

  return (
    <>
      <h1 tabIndex={-1}>Register member</h1>
      <MemberForm submitLabel="Register member" onSubmit={submit} />
    </>
  )
}

function toFormValues(member: MemberResponse): MemberFormValues {
  return { name: member.name, email: member.email }
}

function BackToMembers() {
  return <Link to="/members">Back to Members</Link>
}

function EditMemberPage() {
  usePageTitle('Edit member')
  const { id } = useParams()
  // A non-numeric id can never match a member: treat it as "not found" without calling the API.
  const memberId = /^\d+$/.test(id ?? '') ? Number(id) : null

  return (
    <>
      <h1 tabIndex={-1}>Edit member</h1>
      {memberId === null ? (
        <ErrorState message="This member could not be found.">
          <BackToMembers />
        </ErrorState>
      ) : (
        <EditMemberLoader memberId={memberId} />
      )}
    </>
  )
}

function EditMemberLoader({ memberId }: { memberId: number }) {
  const query = useMember(memberId)

  if (query.isError) {
    // A 4xx (for example a member that no longer exists) will not change on retry, so no Retry button (DR-19).
    const retryable = !isApiError(query.error) || query.error.status >= 500 || query.error.isBackendUnavailable
    return (
      <ErrorState error={query.error} onRetry={retryable ? () => void query.refetch() : undefined} retrying={query.isFetching}>
        <BackToMembers />
      </ErrorState>
    )
  }
  // Wait for a fetch made after this page opened, so the form never starts from a stale cached copy.
  if (query.isPending || !query.isFetchedAfterMount) return <LoadingState label="Loading member…" />
  return <EditMemberForm member={query.data} />
}

function EditMemberForm({ member }: { member: MemberResponse }) {
  const navigate = useNavigate()
  const { notify } = useNotifications()
  const updateMember = useUpdateMember(member.id)

  async function submit(request: MemberRequest) {
    const saved = await updateMember.mutateAsync(request)
    notify(`"${saved.name}" was saved.`)
    await navigate('/members')
  }

  return <MemberForm initialValues={toFormValues(member)} submitLabel="Save changes" onSubmit={submit} />
}
