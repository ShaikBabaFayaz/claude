import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BookFilters as BookFilterValues } from '../../api/types'
import { expectNoA11yViolations } from '../../test/axe'
import { restoreRealTimers, useFakeTimersForTesting } from '../../test/fakeTimers'
import { BookFilters } from './BookFilters'

function setupUser() {
  return userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) })
}

const title = () => screen.getByLabelText('Title') as HTMLInputElement
const author = () => screen.getByLabelText('Author') as HTMLInputElement
const isbn = () => screen.getByLabelText('ISBN') as HTMLInputElement
const availability = () => screen.getByLabelText('Availability') as HTMLSelectElement

/** A parent that, like BooksPage, feeds `onChange` back in as the applied filters (the URL). */
function Parent({ initial = {}, onChange }: { initial?: BookFilterValues; onChange: (next: BookFilterValues) => void }) {
  const [filters, setFilters] = useState<BookFilterValues>(initial)
  return (
    <BookFilters
      filters={filters}
      onChange={(next) => {
        onChange(next)
        setFilters(next)
      }}
      onClear={() => setFilters({})}
    />
  )
}

describe('BookFilters (AC-2.FE.1, AC-2.FE.2, DR-18)', () => {
  beforeEach(() => useFakeTimersForTesting())
  afterEach(() => restoreRealTimers())

  it('AC-2.FE.1 offers title, author and ISBN inputs and an availability select with All / Available / All copies out', () => {
    render(<BookFilters filters={{}} onChange={() => {}} onClear={() => {}} />)
    expect(title()).toHaveValue('')
    expect(author()).toHaveValue('')
    expect(isbn()).toHaveValue('')
    expect(availability()).toHaveValue('')
    expect(Array.from(availability().options).map((option) => [option.value, option.text])).toEqual([
      ['', 'All'],
      ['available', 'Available'],
      ['unavailable', 'All copies out'],
    ])
    expect(screen.getByRole('search', { name: 'Filter books' })).toBeInTheDocument()
  })

  it('AC-2.FE.1 shows the filters that are applied in the URL', () => {
    render(
      <BookFilters
        filters={{ title: 'clean', author: 'martin', isbn: '9780132350884', availability: 'unavailable' }}
        onChange={() => {}}
        onClear={() => {}}
      />,
    )
    expect(title()).toHaveValue('clean')
    expect(author()).toHaveValue('martin')
    expect(isbn()).toHaveValue('9780132350884')
    expect(availability()).toHaveValue('unavailable')
  })

  it('AC-2.FE.2 applies typed text only after a 300 ms pause, as one change for a burst of typing', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(title(), 'clean')
    act(() => vi.advanceTimersByTime(299))
    expect(onChange).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(1))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange).toHaveBeenCalledWith({ title: 'clean', author: undefined, isbn: undefined, availability: undefined })
  })

  it('AC-2.FE.2 restarts the wait with every keystroke', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(author(), 'mar')
    act(() => vi.advanceTimersByTime(250))
    await user.type(author(), 'tin')
    act(() => vi.advanceTimersByTime(250))
    expect(onChange).not.toHaveBeenCalled()
    act(() => vi.advanceTimersByTime(50))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toMatchObject({ author: 'martin' })
  })

  it('AC-2.FE.2 trims the text and treats blank input as no filter', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(title(), '  clean ')
    await user.type(isbn(), '   ')
    act(() => vi.advanceTimersByTime(300))
    expect(onChange).toHaveBeenCalledWith({ title: 'clean', author: undefined, isbn: undefined, availability: undefined })
  })

  it('AC-2.FE.2 applies at once on submit (Enter) and does not apply the same filters again after the pause', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(title(), 'clean{Enter}')
    expect(onChange).toHaveBeenCalledTimes(1)
    act(() => vi.advanceTimersByTime(1000))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(title()).toHaveValue('clean')
  })

  it('AC-2.FE.2 applies at once when the Search button is pressed', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(isbn(), '978-0-13-235088-4')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0]).toMatchObject({ isbn: '978-0-13-235088-4' })
  })

  it('AC-2.FE.2 applies a change of availability at once, together with the text typed so far', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent onChange={onChange} />)
    await user.type(title(), 'code')
    await user.selectOptions(availability(), 'available')
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange).toHaveBeenCalledWith({ title: 'code', author: undefined, isbn: undefined, availability: 'available' })
    act(() => vi.advanceTimersByTime(1000))
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('AC-2.FE.2 does not apply anything when typing ends on the filters that are already applied', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    render(<Parent initial={{ title: 'clean' }} onChange={onChange} />)
    await user.type(title(), 'x{Backspace}')
    act(() => vi.advanceTimersByTime(1000))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('keeps what the user types next while an earlier change is being applied (no reset by its own echo)', async () => {
    const user = setupUser()
    render(<Parent onChange={() => {}} />)
    await user.type(title(), 'cle')
    act(() => vi.advanceTimersByTime(300)) // applied: the parent now holds { title: 'cle' }
    await user.type(title(), 'an')
    expect(title()).toHaveValue('clean')
    act(() => vi.advanceTimersByTime(300))
    expect(title()).toHaveValue('clean')
  })

  it('follows the URL when it changes elsewhere (Back, Clear filters) and does not apply the old text again', async () => {
    const user = setupUser()
    const onChange = vi.fn()
    const view = render(<BookFilters filters={{ title: 'clean' }} onChange={onChange} onClear={() => {}} />)
    await user.type(author(), 'mar') // typed, still inside the pause
    view.rerender(<BookFilters filters={{ title: 'design' }} onChange={onChange} onClear={() => {}} />)
    expect(title()).toHaveValue('design')
    expect(author()).toHaveValue('')
    act(() => vi.advanceTimersByTime(1000))
    expect(onChange).not.toHaveBeenCalled()
    view.rerender(<BookFilters filters={{}} onChange={onChange} onClear={() => {}} />)
    expect(title()).toHaveValue('')
    act(() => vi.advanceTimersByTime(1000))
    expect(onChange).not.toHaveBeenCalled()
  })

  it('DR-18 limits every text input to 255 characters', () => {
    render(<BookFilters filters={{}} onChange={() => {}} onClear={() => {}} />)
    for (const input of [title(), author(), isbn()]) expect(input).toHaveAttribute('maxlength', '255')
  })

  it('AC-2.FE.6 shows "Clear filters" only when asked to, and calls onClear', async () => {
    const user = setupUser()
    const onClear = vi.fn()
    const view = render(<BookFilters filters={{ title: 'a' }} onChange={() => {}} onClear={onClear} showClear={false} />)
    expect(screen.queryByRole('button', { name: 'Clear filters' })).not.toBeInTheDocument()
    view.rerender(<BookFilters filters={{ title: 'a' }} onChange={() => {}} onClear={onClear} showClear />)
    await user.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(onClear).toHaveBeenCalledTimes(1)
  })

  it('has no axe violations', async () => {
    restoreRealTimers()
    const { container } = render(<BookFilters filters={{ title: 'a' }} onChange={() => {}} onClear={() => {}} />)
    await expectNoA11yViolations(container)
  })
})
