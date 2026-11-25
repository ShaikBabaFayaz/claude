import { Link } from 'react-router'
import type { MemberResponse } from '../../api/types'
import { StatusBadge } from '../../components/StatusBadge'
import styles from './MemberTable.module.css'

interface MemberTableProps {
  members: MemberResponse[]
  onDeactivate: (member: MemberResponse) => void
  onReactivate: (member: MemberResponse) => void
  /** Id of the member whose reactivation is running; that button is disabled meanwhile. */
  busyMemberId?: number
}

/**
 * Members table (AC-3.FE.1): name, email, an Active/Inactive label (text plus shape, never colour
 * alone) and the row actions. An active member can be edited or deactivated, an inactive one
 * edited or reactivated (AC-3.FE.5). Member data is always rendered as text (NFR-6).
 */
export function MemberTable({ members, onDeactivate, onReactivate, busyMemberId }: MemberTableProps) {
  return (
    <table className="table">
      <caption>Library members</caption>
      <thead>
        <tr>
          <th scope="col">Name</th>
          <th scope="col">Email</th>
          <th scope="col">Status</th>
          <th scope="col">Actions</th>
        </tr>
      </thead>
      <tbody>
        {members.map((member) => (
          <tr key={member.id}>
            <td className={styles.name}>{member.name}</td>
            <td className={styles.email}>{member.email}</td>
            <td>
              {member.status === 'ACTIVE' ? (
                <StatusBadge tone="success">Active</StatusBadge>
              ) : (
                <StatusBadge tone="neutral">Inactive</StatusBadge>
              )}
            </td>
            <td>
              <div className={styles.actions}>
                <Link to={`/members/${member.id}/edit`} className="button button--small">
                  Edit<span className="visually-hidden"> {member.name}</span>
                </Link>
                {member.status === 'ACTIVE' ? (
                  <button
                    type="button"
                    className="button button--small button--danger"
                    onClick={() => onDeactivate(member)}
                  >
                    Deactivate<span className="visually-hidden"> {member.name}</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    className="button button--small"
                    disabled={busyMemberId === member.id}
                    onClick={() => onReactivate(member)}
                  >
                    Reactivate<span className="visually-hidden"> {member.name}</span>
                  </button>
                )}
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
