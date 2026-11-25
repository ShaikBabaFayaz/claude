import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../../test/axe'
import { makeBook } from '../../test/handlers/books'
import { renderWithProviders } from '../../test/render'
import { BookTable } from './BookTable'

describe('AC-2.FE.3 availability badges', () => {
  const books = [
    makeBook({ id: 1, title: 'Many free', totalCopies: 5, availableCopies: 4 }),
    makeBook({ id: 2, title: 'One free', totalCopies: 3, availableCopies: 1 }),
    makeBook({ id: 3, title: 'None free', totalCopies: 2, availableCopies: 0 }),
  ]

  function rowOf(title: string): HTMLElement {
    return screen.getByRole('cell', { name: title }).closest('tr') as HTMLElement
  }

  it('AC-2.FE.3 shows "Available (n)" with the number of free copies when at least one copy is free', () => {
    renderWithProviders(<BookTable books={books} onDelete={() => {}} />)
    expect(within(rowOf('Many free')).getByText('Available (4)')).toBeInTheDocument()
    expect(within(rowOf('One free')).getByText('Available (1)')).toBeInTheDocument()
  })

  it('AC-2.FE.3 shows "All copies out" when no copy is free', () => {
    renderWithProviders(<BookTable books={books} onDelete={() => {}} />)
    const badge = within(rowOf('None free')).getByText('All copies out')
    expect(badge).toBeInTheDocument()
    expect(within(rowOf('None free')).queryByText(/^Available/)).not.toBeInTheDocument()
  })

  it('AC-2.FE.3 conveys the state by text and shape, not by colour alone', () => {
    renderWithProviders(<BookTable books={books} onDelete={() => {}} />)
    const available = within(rowOf('One free')).getByText('Available (1)')
    const out = within(rowOf('None free')).getByText('All copies out')
    // The words carry the meaning for assistive technology...
    expect(available.textContent).toContain('Available')
    expect(out.textContent).toContain('All copies out')
    // ...and a decorative, different shape per state helps sighted users who cannot tell colours apart.
    const shapeOf = (badge: HTMLElement) => badge.querySelector('[aria-hidden="true"]')?.textContent
    expect(shapeOf(available)).toBeTruthy()
    expect(shapeOf(out)).toBeTruthy()
    expect(shapeOf(available)).not.toBe(shapeOf(out))
  })

  it('AC-2.FE.3 has no axe violations', async () => {
    const { container } = renderWithProviders(<BookTable books={books} onDelete={() => {}} />)
    await expectNoA11yViolations(container)
  })
})
