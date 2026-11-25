import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { FormField } from './FormField'

function TitleField(props: { error?: string; hint?: string; required?: boolean }) {
  return (
    <FormField label="Title" {...props}>
      {(control) => <input className="input" type="text" {...control} />}
    </FormField>
  )
}

describe('FormField (AC-1.FE.2, AC-3.FE.2, NFR-3)', () => {
  it('associates the label with the control', async () => {
    const user = userEvent.setup()
    render(<TitleField />)
    const input = screen.getByLabelText('Title')
    expect(input.tagName).toBe('INPUT')
    await user.click(screen.getByText('Title'))
    expect(input).toHaveFocus()
  })

  it('has no error attributes while valid', () => {
    render(<TitleField />)
    const input = screen.getByLabelText('Title')
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(input).not.toHaveAttribute('aria-describedby')
  })

  it('links the inline error through aria-describedby and marks the control invalid', () => {
    render(<TitleField error="Title is required." />)
    const input = screen.getByLabelText('Title')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Error: Title is required.')
  })

  it('describes the control with both hint and error, hint first', () => {
    render(<TitleField hint="At most 255 characters." error="Title is required." />)
    expect(screen.getByLabelText('Title')).toHaveAccessibleDescription('At most 255 characters. Error: Title is required.')
  })

  it('shows the error as text with a marker, not by colour alone', () => {
    render(<TitleField error="Title is required." />)
    expect(screen.getByText('Title is required.')).toBeVisible()
    expect(screen.getByText('Error:')).toHaveClass('visually-hidden')
  })

  it('marks a required field in the label text and with aria-required', () => {
    render(<TitleField required />)
    expect(screen.getByLabelText(/Title/)).toHaveAttribute('aria-required', 'true')
    expect(screen.getByText('(required)')).toBeInTheDocument()
  })

  it('gives each field instance its own ids', () => {
    render(
      <>
        <TitleField error="First." />
        <TitleField error="Second." />
      </>,
    )
    const [first, second] = screen.getAllByLabelText('Title')
    expect(first.id).not.toBe(second.id)
    expect(first).toHaveAccessibleDescription('Error: First.')
    expect(second).toHaveAccessibleDescription('Error: Second.')
  })

  it('has no axe violations with and without an error', async () => {
    const { container, rerender } = render(<TitleField hint="Hint" required />)
    await expectNoA11yViolations(container)
    rerender(<TitleField hint="Hint" required error="Title is required." />)
    await expectNoA11yViolations(container)
  })
})
