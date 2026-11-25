import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { Pagination } from './Pagination'

describe('Pagination (AC-2.FE.4)', () => {
  it('is a labelled navigation landmark showing a one-based "Page x of y"', () => {
    render(<Pagination page={1} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
  })

  it('passes the zero-based index of the previous and next page', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={3} onPageChange={onPageChange} />)
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenLastCalledWith(2)
    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(onPageChange).toHaveBeenLastCalledWith(0)
  })

  it('disables Previous on the first page and Next on the last page', () => {
    const { rerender } = render(<Pagination page={0} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled()
    rerender(<Pagination page={2} totalPages={3} onPageChange={() => {}} />)
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it.each([0, 1])('renders nothing when there are %i pages', (totalPages) => {
    const { container } = render(<Pagination page={0} totalPages={totalPages} onPageChange={() => {}} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('is operable by keyboard', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={2} onPageChange={onPageChange} />)
    await user.tab()
    expect(screen.getByRole('button', { name: 'Next' })).toHaveFocus() // Previous is disabled
    await user.keyboard('{Enter}')
    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('has no axe violations', async () => {
    const { container } = render(<Pagination page={1} totalPages={3} onPageChange={() => {}} />)
    await expectNoA11yViolations(container)
  })
})
