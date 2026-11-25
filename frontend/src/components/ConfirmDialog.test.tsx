import { act, fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { expectNoA11yViolations } from '../test/axe'
import { renderWithProviders } from '../test/render'
import { ConfirmDialog } from './ConfirmDialog'

interface HarnessProps {
  onConfirm?: () => void
  /** Remove the trigger button from the page when the dialog is confirmed (like a deleted row). */
  removeTriggerOnConfirm?: boolean
  busy?: boolean
}

function Harness({ onConfirm = () => {}, removeTriggerOnConfirm = false, busy = false }: HarnessProps) {
  const [open, setOpen] = useState(false)
  const [rowExists, setRowExists] = useState(true)
  return (
    <>
      {rowExists && (
        <button type="button" onClick={() => setOpen(true)}>
          Delete Clean Code
        </button>
      )}
      <button type="button">Other button</button>
      <ConfirmDialog
        open={open}
        title="Delete this book?"
        confirmLabel="Delete"
        danger
        busy={busy}
        onCancel={() => setOpen(false)}
        onConfirm={() => {
          onConfirm()
          setOpen(false)
          if (removeTriggerOnConfirm) setRowExists(false)
        }}
      >
        Clean Code will be removed from the catalogue.
      </ConfirmDialog>
    </>
  )
}

describe('ConfirmDialog (AC-1.FE.4, AC-3.FE.3, DR-11)', () => {
  it('is not in the page while closed', () => {
    renderWithProviders(<Harness />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('opens as a native modal <dialog> labelled by its heading and described by its text', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Harness />)
    await user.click(screen.getByRole('button', { name: 'Delete Clean Code' }))

    const dialog = screen.getByRole('dialog', { name: 'Delete this book?' })
    expect(dialog.tagName).toBe('DIALOG')
    expect(dialog).toHaveAttribute('open')
    expect(dialog).toHaveAccessibleDescription('Clean Code will be removed from the catalogue.')
  })

  it('puts initial focus on Cancel, the safe choice', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Harness />)
    await user.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('calls onConfirm only after the confirm button is pressed', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    renderWithProviders(<Harness onConfirm={onConfirm} />)
    await user.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    expect(onConfirm).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('Cancel closes the dialog without confirming and returns focus to the trigger', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    renderWithProviders(<Harness onConfirm={onConfirm} />)
    const trigger = screen.getByRole('button', { name: 'Delete Clean Code' })
    await user.click(trigger)
    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('Esc (the dialog "cancel" event) cancels without confirming and returns focus to the trigger', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    renderWithProviders(<Harness onConfirm={onConfirm} />)
    const trigger = screen.getByRole('button', { name: 'Delete Clean Code' })
    await user.click(trigger)
    // jsdom does not turn the Escape key into a dialog "cancel" event as browsers do, so the event is fired directly.
    const cancelEvent = new Event('cancel', { cancelable: true })
    act(() => {
      screen.getByRole('dialog').dispatchEvent(cancelEvent)
    })
    expect(cancelEvent.defaultPrevented).toBe(true)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
    expect(trigger).toHaveFocus()
  })

  it('restores focus only if the trigger still exists: a removed trigger is left alone (DR-11)', async () => {
    const user = userEvent.setup()
    renderWithProviders(<Harness removeTriggerOnConfirm />)
    await user.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(screen.queryByRole('button', { name: 'Delete Clean Code' })).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    // The dialog did not focus a detached element and did not throw. Choosing the next focus
    // target (the success notice) is the page's job, covered by the screen tests of T-12/T-20.
    expect(document.activeElement).toBe(document.body)
  })

  it('disables both buttons and ignores Esc while busy', async () => {
    const user = userEvent.setup()
    function BusyHarness() {
      const [open, setOpen] = useState(true)
      return (
        <ConfirmDialog open={open} title="Working" confirmLabel="Confirm" busy onCancel={() => setOpen(false)} onConfirm={() => {}}>
          Please wait.
        </ConfirmDialog>
      )
    }
    renderWithProviders(<BusyHarness />)
    expect(screen.getByRole('button', { name: 'Confirm' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('has no axe violations while open', async () => {
    const user = userEvent.setup()
    const { container } = renderWithProviders(<Harness />)
    await user.click(screen.getByRole('button', { name: 'Delete Clean Code' }))
    await expectNoA11yViolations(container)
  })
})
