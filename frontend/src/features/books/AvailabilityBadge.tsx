import { StatusBadge } from '../../components/StatusBadge'

/**
 * "Available (n)" when at least one copy is free, "All copies out" otherwise (AC-2.FE.3, FR-9).
 * The state is in the text and in the badge shape, never in colour alone (NFR-3).
 */
export function AvailabilityBadge({ availableCopies }: { availableCopies: number }) {
  if (availableCopies >= 1) return <StatusBadge tone="success">Available ({availableCopies})</StatusBadge>
  return <StatusBadge tone="danger">All copies out</StatusBadge>
}
