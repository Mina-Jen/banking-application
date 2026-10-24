import { useRef, useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Sheet } from './Sheet'

afterEach(() => {
  cleanup()
  // The sheet locks body scroll; a failed assertion must not leak that into the
  // next test file.
  document.body.style.overflow = ''
})

function renderSheet(props: Partial<React.ComponentProps<typeof Sheet>> = {}) {
  const onDismiss = vi.fn()
  const view = render(
    <Sheet
      open
      title="Choose an account"
      description="Savings ends 4821."
      onDismiss={onDismiss}
      {...props}
    >
      <p>Account list</p>
    </Sheet>,
  )
  return { ...view, onDismiss }
}

describe('Sheet', () => {
  it('renders nothing while closed', () => {
    renderSheet({ open: false })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('is a labelled modal that holds its own content and footer', () => {
    renderSheet({ footer: <button type="button">Done</button> })

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAccessibleName('Choose an account')
    expect(dialog).toHaveAccessibleDescription('Savings ends 4821.')
    expect(screen.getByText('Account list')).toBeInTheDocument()
    expect(dialog).toContainElement(screen.getByRole('button', { name: 'Done' }))
  })

  it('describes itself only when there is something to describe', () => {
    renderSheet({ description: undefined })

    expect(screen.getByRole('dialog')).not.toHaveAttribute('aria-describedby')
  })

  it('puts the dismiss cross on the bottom variant only', () => {
    const { unmount } = renderSheet({ variant: 'bottom' })
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()

    unmount()
    renderSheet({ variant: 'center' })
    // A centred dialog is a decision with named outcomes. An "X" would be a
    // second word for "Cancel" — and a new first stop for the Tab key.
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument()
  })

  it('closes from the bottom-variant cross', async () => {
    const { onDismiss } = renderSheet({ variant: 'bottom' })

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('focuses the panel when the caller names no landing spot', () => {
    renderSheet()

    expect(screen.getByRole('dialog')).toHaveFocus()
  })

  it('focuses the ref the caller names instead', () => {
    function Harness() {
      const confirmRef = useRef<HTMLButtonElement>(null)
      return (
        <Sheet
          open
          title="Confirm withdrawal"
          footer={
            <button ref={confirmRef} type="button">
              Withdraw
            </button>
          }
          initialFocusRef={confirmRef}
          onDismiss={() => {}}
        />
      )
    }
    render(<Harness />)

    expect(screen.getByRole('button', { name: 'Withdraw' })).toHaveFocus()
  })

  it('closes on Escape', async () => {
    const { onDismiss } = renderSheet()

    await userEvent.keyboard('{Escape}')

    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('closes on a backdrop press but not on a press inside the panel', () => {
    const { onDismiss } = renderSheet()
    const dialog = screen.getByRole('dialog')

    fireEvent.mouseDown(dialog)
    expect(onDismiss).not.toHaveBeenCalled()

    fireEvent.mouseDown(dialog.parentElement as HTMLElement)
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('refuses every dismissal while busy, including the cross', async () => {
    const { onDismiss } = renderSheet({ variant: 'bottom', busy: true })

    expect(screen.getByRole('dialog')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    fireEvent.mouseDown(screen.getByRole('dialog').parentElement as HTMLElement)
    await userEvent.keyboard('{Escape}')

    expect(onDismiss).not.toHaveBeenCalled()
  })

  it('locks page scroll while open and restores it on close', () => {
    const { rerender } = render(<Sheet open title="t" onDismiss={() => {}} />)
    expect(document.body.style.overflow).toBe('hidden')

    rerender(<Sheet open={false} title="t" onDismiss={() => {}} />)
    expect(document.body.style.overflow).toBe('')
  })

  it('returns focus to whatever opened it', async () => {
    function Harness() {
      const [visible, setVisible] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setVisible(true)}>
            Open sheet
          </button>
          {visible ? (
            <Sheet open title="Choose an account" onDismiss={() => setVisible(false)}>
              <button type="button">Cancel</button>
            </Sheet>
          ) : null}
        </>
      )
    }
    render(<Harness />)

    await userEvent.click(screen.getByRole('button', { name: 'Open sheet' }))
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open sheet' })).toHaveFocus()
  })

  it('lets only the top dialog handle Escape when a confirmation is stacked over a sheet', async () => {
    const dismissAction = vi.fn()
    const dismissConfirm = vi.fn()
    render(
      <>
        <Sheet open title="Transfer" onDismiss={dismissAction}>
          <p>Transfer form</p>
        </Sheet>
        <Sheet open title="Confirm transfer" onDismiss={dismissConfirm}>
          <p>Transfer summary</p>
        </Sheet>
      </>,
    )

    await userEvent.keyboard('{Escape}')

    expect(dismissConfirm).toHaveBeenCalledTimes(1)
    expect(dismissAction).not.toHaveBeenCalled()
  })
})
