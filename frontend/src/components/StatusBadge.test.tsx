import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { StatusBadge, type BadgeTone } from './StatusBadge'

describe('StatusBadge (NFR-3, AC-2.FE.1, AC-3.FE.1)', () => {
  it('conveys the state as text', () => {
    render(<StatusBadge tone="success">Available (2)</StatusBadge>)
    expect(screen.getByText('Available (2)')).toBeInTheDocument()
  })

  it('uses a different shape per tone, hidden from assistive technology', () => {
    const tones: BadgeTone[] = ['success', 'danger', 'warning', 'neutral']
    const shapes = tones.map((tone) => {
      const { container, unmount } = render(<StatusBadge tone={tone}>State</StatusBadge>)
      const shape = container.querySelector('[aria-hidden="true"]')
      const text = shape?.textContent ?? ''
      unmount()
      expect(text).not.toBe('')
      return text
    })
    expect(new Set(shapes).size).toBe(tones.length)
  })

  it('has no axe violations for any tone', async () => {
    const { container } = render(
      <>
        <StatusBadge tone="success">Active</StatusBadge>
        <StatusBadge tone="danger">All copies out</StatusBadge>
        <StatusBadge tone="warning">Overdue by 6 days</StatusBadge>
        <StatusBadge tone="neutral">Inactive</StatusBadge>
      </>,
    )
    await expectNoA11yViolations(container)
  })
})
