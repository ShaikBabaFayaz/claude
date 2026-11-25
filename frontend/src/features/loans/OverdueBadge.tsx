import { StatusBadge } from '../../components/StatusBadge'

interface OverdueBadgeProps {
  daysOverdue: number
  /** `by`: "Overdue by 6 days" (member's loan list). `count`: "6 days overdue" (overdue page). */
  wording?: 'by' | 'count'
}

/** Late status in words plus badge shape and colour, never colour alone (AC-4.FE.4, AC-4.FE.5). */
export function OverdueBadge({ daysOverdue, wording = 'by' }: OverdueBadgeProps) {
  const days = `${daysOverdue} ${daysOverdue === 1 ? 'day' : 'days'}`
  return <StatusBadge tone="danger">{wording === 'by' ? `Overdue by ${days}` : `${days} overdue`}</StatusBadge>
}
