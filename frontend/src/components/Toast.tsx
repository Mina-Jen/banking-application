import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { IconAlert, IconCheck, IconClose, IconInfo } from './Icon'
import { cx, focusRingOnDark } from './ui'

type Tone = 'success' | 'error' | 'info'

const TONE: Record<Tone, { panel: string; icon: ReactNode }> = {
  success: { panel: 'bg-success text-on-dark', icon: <IconCheck className="h-5 w-5" /> },
  error: { panel: 'bg-danger text-on-dark', icon: <IconAlert className="h-5 w-5" /> },
  info: { panel: 'bg-ink-900 text-on-dark', icon: <IconInfo className="h-5 w-5" /> },
}

interface ToastProps {
  open: boolean
  tone?: Tone
  message: ReactNode
  onDismiss(): void
  /**
   * Milliseconds before it closes itself. Defaults to 4000 for a confirmation
   * and to 0 — never — for an error: a red banner that disappears on a timer
   * takes the recovery instructions with it.
   */
  duration?: number
}

/**
 * A one-line result message that floats over the page instead of pushing it
 * around.
 *
 * It is polite by default (`role="status"`) and never steals focus, so a user
 * mid-way through a form is not yanked sideways; an error is the one case that
 * has to interrupt, so it announces itself. The dismiss control is always
 * present, because a message that cannot be closed is a message that cannot be
 * ignored.
 */
export function Toast({ open, tone = 'info', message, onDismiss, duration }: ToastProps) {
  const timeout = duration ?? (tone === 'error' ? 0 : 4000)

  useEffect(() => {
    if (!open || timeout <= 0) {
      return
    }
    const timer = setTimeout(onDismiss, timeout)
    return () => clearTimeout(timer)
  }, [open, timeout, onDismiss])

  if (!open) {
    return null
  }

  return createPortal(
    <div className="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex justify-center px-4 pb-6 sm:justify-end sm:px-6">
      <div
        role={tone === 'error' ? 'alert' : 'status'}
        className={cx(
          'toast-panel pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl px-4 py-3 text-sm shadow-xl',
          TONE[tone].panel,
        )}
      >
        <span aria-hidden="true" className="mt-0.5 shrink-0">
          {TONE[tone].icon}
        </span>
        <span className="min-w-0 flex-1">{message}</span>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss message"
          className={cx(
            '-m-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full opacity-80 hover:opacity-100',
            focusRingOnDark,
          )}
        >
          <IconClose className="h-4 w-4" />
        </button>
      </div>
    </div>,
    document.body,
  )
}
