import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

afterEach(() => {
  cleanup()
  // The dialog locks body scroll; a failed assertion must not leak that.
  document.body.style.overflow = ''
})

function renderDialog(props: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  const view = render(
    <ConfirmDialog
      open
      title="Confirm deposit"
      description="This moves money immediately."
      confirmLabel="Deposit ₱25.00"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    >
      <p>Amount ₱25.00</p>
    </ConfirmDialog>,
  )
  return { ...view, onConfirm, onCancel }
}

describe('ConfirmDialog', () => {
  it('renders nothing while closed', () => {
    renderDialog({ open: false })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('exposes a labelled, modal dialog with the summary inside', () => {
    renderDialog()

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAccessibleName('Confirm deposit')
    expect(dialog).toHaveAccessibleDescription('This moves money immediately.')
    expect(screen.getByText('Amount ₱25.00')).toBeInTheDocument()
  })

  it('renders above the sheet that opened it', () => {
    renderDialog()

    expect(screen.getByRole('dialog').parentElement).toHaveClass('z-[70]')
  })

  it('moves focus to Cancel, not Confirm, so a stray Enter does nothing', () => {
    renderDialog()

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('calls onConfirm when the action button is pressed', async () => {
    const { onConfirm, onCancel } = renderDialog()

    await userEvent.click(screen.getByRole('button', { name: 'Deposit ₱25.00' }))

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onCancel).not.toHaveBeenCalled()
  })

  it('closes on Escape', async () => {
    const { onCancel } = renderDialog()

    await userEvent.keyboard('{Escape}')

    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('closes on a backdrop press but not on a press inside the panel', () => {
    const { onCancel } = renderDialog()
    const dialog = screen.getByRole('dialog')
    const backdrop = dialog.parentElement as HTMLElement

    fireEvent.mouseDown(dialog)
    expect(onCancel).not.toHaveBeenCalled()

    fireEvent.mouseDown(backdrop)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('traps Tab inside the dialog', async () => {
    renderDialog()

    const cancel = screen.getByRole('button', { name: 'Cancel' })
    const confirm = screen.getByRole('button', { name: 'Deposit ₱25.00' })

    confirm.focus()
    await userEvent.tab()
    expect(cancel).toHaveFocus()

    await userEvent.tab({ shift: true })
    expect(confirm).toHaveFocus()
  })

  it('keeps body scroll locked while open and restores it on close', () => {
    const { rerender } = render(
      <ConfirmDialog open title="t" confirmLabel="Go" onConfirm={() => {}} onCancel={() => {}} />,
    )
    expect(document.body.style.overflow).toBe('hidden')

    rerender(
      <ConfirmDialog open={false} title="t" confirmLabel="Go" onConfirm={() => {}} onCancel={() => {}} />,
    )
    expect(document.body.style.overflow).toBe('')
  })

  it('returns focus to whatever opened it', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            Open dialog
          </button>
          <ConfirmDialog
            open={open}
            title="Confirm deposit"
            confirmLabel="Deposit"
            onConfirm={() => setOpen(false)}
            onCancel={() => setOpen(false)}
          />
        </>
      )
    }
    render(<Harness />)

    await userEvent.click(screen.getByRole('button', { name: 'Open dialog' }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open dialog' })).toHaveFocus()
  })

  it('disables both buttons and ignores Escape while a request is in flight', async () => {
    const { onCancel } = renderDialog({ busy: true, busyLabel: 'Depositing…' })

    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Depositing/ })).toBeDisabled()
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-busy', 'true')

    await userEvent.keyboard('{Escape}')
    expect(onCancel).not.toHaveBeenCalled()
  })

  it('ignores a backdrop press while busy', () => {
    const { onCancel } = renderDialog({ busy: true })
    const backdrop = screen.getByRole('dialog').parentElement as HTMLElement

    fireEvent.mouseDown(backdrop)

    expect(onCancel).not.toHaveBeenCalled()
  })
})
