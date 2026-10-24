import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Toast } from './Toast'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

function renderToast(props: Partial<React.ComponentProps<typeof Toast>> = {}) {
  const onDismiss = vi.fn()
  const view = render(<Toast open message="Deposit of ₱25.00 accepted." onDismiss={onDismiss} {...props} />)
  return { ...view, onDismiss }
}

describe('Toast', () => {
  it('renders nothing while closed', () => {
    renderToast({ open: false })

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('is polite by default, because a confirmation must not interrupt a form', () => {
    renderToast()

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('interrupts for an error', () => {
    renderToast({ tone: 'error', message: 'Withdrawal rejected: insufficient funds.' })

    expect(screen.getByRole('alert')).toHaveTextContent('insufficient funds')
  })

  it('closes a confirmation on its own after four seconds', () => {
    vi.useFakeTimers()
    const { onDismiss } = renderToast()

    vi.advanceTimersByTime(3999)
    expect(onDismiss).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('never closes an error on a timer — the message carries the fix', () => {
    vi.useFakeTimers()
    const { onDismiss } = renderToast({ tone: 'error' })

    vi.advanceTimersByTime(60_000)

    expect(onDismiss).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('takes a duration from the caller, and zero means never', () => {
    vi.useFakeTimers()
    const quick = renderToast({ duration: 1000 })
    vi.advanceTimersByTime(1000)
    expect(quick.onDismiss).toHaveBeenCalledTimes(1)

    cleanup()

    const sticky = renderToast({ duration: 0 })
    vi.advanceTimersByTime(60_000)
    expect(sticky.onDismiss).not.toHaveBeenCalled()
  })

  it('does not fire a stale dismissal after the toast is closed', () => {
    vi.useFakeTimers()
    const onDismiss = vi.fn()
    const { rerender } = render(
      <Toast open message="Deposit accepted." onDismiss={onDismiss} />,
    )

    rerender(<Toast open={false} message="Deposit accepted." onDismiss={onDismiss} />)
    vi.advanceTimersByTime(10_000)

    // Same onDismiss identity, so the effect cleanup must have cancelled it.
    expect(onDismiss).not.toHaveBeenCalled()
  })

  it('can always be dismissed by hand', async () => {
    const { onDismiss } = renderToast({ tone: 'error' })

    await userEvent.click(screen.getByRole('button', { name: 'Dismiss message' }))

    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('keeps the icon decorative so the words are the message', () => {
    renderToast({ tone: 'success' })

    // The toast is a portal, so the search starts at the announced element.
    const status = screen.getByRole('status')
    expect(status.querySelector('[aria-hidden="true"] svg')).not.toBeNull()
    expect(status).toHaveTextContent('₱25.00 accepted')
  })
})
